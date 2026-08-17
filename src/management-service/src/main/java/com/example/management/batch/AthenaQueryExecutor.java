package com.example.management.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.athena.AthenaClient;
import software.amazon.awssdk.services.athena.model.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Athena에 쿼리를 던지고, 완료될 때까지 폴링한 뒤, 결과를 페이징해서 전부 읽어온다.
 * StartQueryExecution -> GetQueryExecution(폴링) -> GetQueryResults(페이징) 3단계를 감싼 유틸.
 */
@Slf4j
@Component
@Profile("batch")
@RequiredArgsConstructor
public class AthenaQueryExecutor {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final int MAX_POLL_ATTEMPTS = 150; // 최대 5분 대기

    private final AthenaClient athenaClient;

    /**
     * 쿼리를 실행하고 결과를 rowMapper로 변환한 리스트를 반환한다.
     * 첫 번째 결과 행(헤더)은 자동으로 건너뛴다.
     */
    public <T> List<T> execute(String sql, String workgroup, Function<List<String>, T> rowMapper) {
        String queryExecutionId = startQuery(sql, workgroup);
        waitForCompletion(queryExecutionId);
        return fetchAllResults(queryExecutionId, rowMapper);
    }

    private String startQuery(String sql, String workgroup) {
        StartQueryExecutionRequest request = StartQueryExecutionRequest.builder()
                .queryString(sql)
                .workGroup(workgroup)
                .build();
        StartQueryExecutionResponse response = athenaClient.startQueryExecution(request);
        log.info("Athena query started. queryExecutionId={}", response.queryExecutionId());
        return response.queryExecutionId();
    }

    private void waitForCompletion(String queryExecutionId) {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            GetQueryExecutionResponse response = athenaClient.getQueryExecution(
                    GetQueryExecutionRequest.builder().queryExecutionId(queryExecutionId).build());

            QueryExecutionState state = response.queryExecution().status().state();
            switch (state) {
                case SUCCEEDED -> {
                    return;
                }
                case FAILED, CANCELLED -> {
                    String reason = response.queryExecution().status().stateChangeReason();
                    throw new AthenaBatchException(
                            "Athena query %s: %s (queryExecutionId=%s)"
                                    .formatted(state, reason, queryExecutionId));
                }
                case QUEUED, RUNNING -> sleep();
                default -> sleep();
            }
        }
        throw new AthenaBatchException(
                "Athena query polling timed out. queryExecutionId=" + queryExecutionId);
    }

    private <T> List<T> fetchAllResults(String queryExecutionId, Function<List<String>, T> rowMapper) {
        List<T> results = new ArrayList<>();
        String nextToken = null;
        boolean firstPage = true;

        do {
            GetQueryResultsRequest.Builder builder = GetQueryResultsRequest.builder()
                    .queryExecutionId(queryExecutionId);
            if (nextToken != null) {
                builder.nextToken(nextToken);
            }
            GetQueryResultsResponse response = athenaClient.getQueryResults(builder.build());

            List<Row> rows = response.resultSet().rows();
            int startIndex = firstPage ? 1 : 0; // 첫 페이지의 첫 행은 컬럼 헤더라 건너뜀
            for (int i = startIndex; i < rows.size(); i++) {
                List<String> values = rows.get(i).data().stream()
                        .map(Datum::varCharValue)
                        .toList();
                results.add(rowMapper.apply(values));
            }

            nextToken = response.nextToken();
            firstPage = false;
        } while (nextToken != null);

        return results;
    }

    private void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AthenaBatchException("Interrupted while polling Athena query", e);
        }
    }

    public static class AthenaBatchException extends RuntimeException {
        public AthenaBatchException(String message) {
            super(message);
        }

        public AthenaBatchException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
