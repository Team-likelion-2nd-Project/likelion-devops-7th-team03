package com.example.management.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * link_daily_stats에 대량 UPSERT. JPA save-if-not-exists는 링크 수만큼 2N 쿼리가
 * 나가므로(엔티티 주석 참고) 네이티브 INSERT ... ON DUPLICATE KEY UPDATE 배치로 처리한다.
 *
 * rewriteBatchedStatements=true(JDBC URL)가 batchUpdate 전체를 하나의 거대한
 * 멀티밸류 INSERT 문자열로 rewrite하려고 시도하는데, 부하테스트 등으로 row 수가
 * 급증하면 rewrite 과정 자체가 heap을 다 잡아먹고 죽는다.
 * chunkSize 단위로 나눠서 배치 실행 자체를 여러 트랜잭션으로 쪼갠다.
 *
 * [재시도 정책] (DimensionStatsUpsertRepository와 동일한 패턴)
 * rewriteBatchedStatements=true 때문에 청크가 사실상 멀티밸류 INSERT 한 문장으로 합쳐져서,
 * 그 안 row 하나만 실패해도 몇 번째 row인지 JDBC가 안 알려주고 문 전체가 롤백된다
 * → 순수 row 단위 부분 재시도는 신뢰 불가. 그래서:
 *   1) 청크 전체를 최대 maxRetry회 지수 백오프로 재시도 (락 타임아웃, 커넥션 순단 등
 *      일시적 오류는 여기서 대부분 해결됨). UPSERT라 재시도해도 중복 반영 위험 없음(멱등적).
 *   2) 그래도 실패하면 청크를 반으로 쪼개 재귀적으로 재시도(bisect) → 문제 row만 좁혀서 격리
 *   3) bisectFloor 이하로 좁혀졌는데도 실패하면 그 row(들)는 dead-letter로 로그만
 *      남기고 skip (배치 전체를 죽이지 않음)
 *
 * [서킷 브레이커]
 * row 몇 개가 이상해서 실패하는 것과 DB 자체가 맛이 가서(커넥션 풀 고갈, DB 다운 등)
 * 실패하는 건 다르다. 후자인데 재시도/bisect만 있으면 남은 청크 전부 재시도+bisect
 * 하느라 잡이 끝도 없이 돈다. → 청크가 연속으로 N번 실패하면 시스템 문제로 판단하고
 * 배치 전체를 즉시 중단시킨다. 청크가 하나라도 성공하면 연속 카운트는 리셋.
 */
@Slf4j
@Repository
public class DailyStatsUpsertRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO link_daily_stats (link_id, stat_date, click_count, visitor_count)
            VALUES (:linkId, :statDate, :clickCount, :visitorCount)
            ON DUPLICATE KEY UPDATE
                click_count = VALUES(click_count),
                visitor_count = VALUES(visitor_count)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // DimensionStatsUpsertRepository와 동일한 이유로 static final → 인스턴스 필드로 뺐다.
    // 테스트에서 작은 값 + no-op Sleeper 주입해서 재시도/bisect/서킷브레이커를 빠르게 검증하기 위함.
    private final int chunkSize;
    private final int maxRetry;
    private final long baseBackoffMillis;
    private final int bisectFloor;
    private final int circuitBreakerThreshold;
    private final Sleeper sleeper;

    @Autowired
    public DailyStatsUpsertRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, 1000, 3, 500L, 5, 3, DEFAULT_SLEEPER);
    }

    /** 테스트 전용 생성자. */
    DailyStatsUpsertRepository(NamedParameterJdbcTemplate jdbcTemplate, int chunkSize, int maxRetry,
                               long baseBackoffMillis, int bisectFloor, int circuitBreakerThreshold, Sleeper sleeper) {
        this.jdbcTemplate = jdbcTemplate;
        this.chunkSize = chunkSize;
        this.maxRetry = maxRetry;
        this.baseBackoffMillis = baseBackoffMillis;
        this.bisectFloor = bisectFloor;
        this.circuitBreakerThreshold = circuitBreakerThreshold;
        this.sleeper = sleeper;
    }

    private static final Sleeper DEFAULT_SLEEPER = millis -> {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트됨", ie);
        }
    };

    /** 재시도 백오프 대기를 추상화. 운영에선 진짜 sleep, 테스트에선 no-op으로 주입해서 빠르게 돌린다. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis);
    }

    public void upsertAll(List<AthenaBatchRunner.DailyStatRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        int totalChunks = (rows.size() + chunkSize - 1) / chunkSize;
        int chunkIndex = 0;
        int failedRowCount = 0;
        int consecutiveFailedChunks = 0;

        for (int i = 0; i < rows.size(); i += chunkSize) {
            chunkIndex++;
            List<AthenaBatchRunner.DailyStatRow> chunk = rows.subList(i, Math.min(i + chunkSize, rows.size()));

            log.info("[daily-stats-upsert] chunk {}/{} 시작. size={}", chunkIndex, totalChunks, chunk.size());
            long startedAt = System.currentTimeMillis();

            List<AthenaBatchRunner.DailyStatRow> deadLetters = upsertWithRetry(chunk, chunkIndex, 1);
            long elapsedMs = System.currentTimeMillis() - startedAt;

            if (deadLetters.isEmpty()) {
                log.info("[daily-stats-upsert] chunk {}/{} 완료. size={}, elapsedMs={}, sample={}",
                        chunkIndex, totalChunks, chunk.size(), elapsedMs, sampleRange(chunk));
                consecutiveFailedChunks = 0;
            } else {
                failedRowCount += deadLetters.size();
                consecutiveFailedChunks++;
                log.error("[daily-stats-upsert] chunk {}/{} 일부 실패. size={}, deadLetters={}, rows={}, consecutiveFailedChunks={}",
                        chunkIndex, totalChunks, chunk.size(), deadLetters.size(), summarize(deadLetters), consecutiveFailedChunks);

                if (consecutiveFailedChunks >= circuitBreakerThreshold) {
                    log.error("[daily-stats-upsert] 서킷 브레이커 작동: 청크 {}회 연속 실패 → 배치 중단. " +
                                    "처리된 청크={}/{}, 지금까지 실패 row={}",
                            consecutiveFailedChunks, chunkIndex, totalChunks, failedRowCount);
                    throw new BatchCircuitBreakerException(
                            "청크 " + consecutiveFailedChunks + "회 연속 실패로 배치 중단 (chunk "
                                    + chunkIndex + "/" + totalChunks + ")");
                }
            }
        }

        log.info("link_daily_stats 전체 UPSERT 완료. totalRows={}, chunkSize={}, failedRows={}",
                rows.size(), chunkSize, failedRowCount);
    }

    /**
     * 청크를 최대 maxRetry회 재시도. 그래도 실패하면 bisect(반으로 쪼개서 재귀 재시도)로
     * 문제 row를 좁혀나간다. 최종적으로 실패한(=dead-letter) row 목록을 반환한다.
     */
    private List<AthenaBatchRunner.DailyStatRow> upsertWithRetry(
            List<AthenaBatchRunner.DailyStatRow> chunk, int chunkIndex, int depth) {

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                upsertChunk(chunk);
                if (attempt > 1) {
                    log.warn("[daily-stats-upsert] chunk {} (depth={}) {}번째 시도에 성공. size={}",
                            chunkIndex, depth, attempt, chunk.size());
                }
                return List.of();
            } catch (DataAccessException e) {
                log.warn("[daily-stats-upsert] chunk {} (depth={}) {}번째 시도 실패. size={}, cause={}",
                        chunkIndex, depth, attempt, chunk.size(), e.getMessage());

                if (attempt == maxRetry) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }

        // maxRetry 다 실패 → bisect 시도
        if (chunk.size() <= bisectFloor) {
            log.error("[daily-stats-upsert] chunk {} (depth={}) bisectFloor({}) 이하로도 실패 → dead-letter 처리. size={}",
                    chunkIndex, depth, bisectFloor, chunk.size());
            return chunk;
        }

        log.warn("[daily-stats-upsert] chunk {} (depth={}) 재시도 소진 → bisect 진행. size={}",
                chunkIndex, depth, chunk.size());

        int mid = chunk.size() / 2;
        List<AthenaBatchRunner.DailyStatRow> left = chunk.subList(0, mid);
        List<AthenaBatchRunner.DailyStatRow> right = chunk.subList(mid, chunk.size());

        List<AthenaBatchRunner.DailyStatRow> deadLetters = new ArrayList<>();
        deadLetters.addAll(upsertWithRetry(left, chunkIndex, depth + 1));
        deadLetters.addAll(upsertWithRetry(right, chunkIndex, depth + 1));
        return deadLetters;
    }

    private void sleepBackoff(int attempt) {
        long backoffMs = baseBackoffMillis * (1L << (attempt - 1)); // 500ms, 1s, 2s...
        sleeper.sleep(backoffMs);
    }

    @Transactional
    protected void upsertChunk(List<AthenaBatchRunner.DailyStatRow> chunk) {
        MapSqlParameterSource[] params = chunk.stream()
                .map(row -> new MapSqlParameterSource()
                        .addValue("linkId", row.linkId())
                        .addValue("statDate", row.statDate())
                        .addValue("clickCount", row.clickCount())
                        .addValue("visitorCount", row.visitorCount()))
                .toArray(MapSqlParameterSource[]::new);

        int[] updateCounts = jdbcTemplate.batchUpdate(UPSERT_SQL, params);

        if (log.isDebugEnabled()) {
            // MySQL ON DUPLICATE KEY UPDATE 반환값 관례: 1=INSERT, 2=UPDATE(값 변경), 0=UPDATE(값 동일해서 변경 없음)
            int inserted = 0, updated = 0, unchanged = 0;
            for (int c : updateCounts) {
                if (c == 1) inserted++;
                else if (c == 2) updated++;
                else unchanged++;
            }
            log.debug("[daily-stats-upsert] batchUpdate 결과: inserted={}, updated={}, unchanged={}",
                    inserted, updated, unchanged);
        }
    }

    /** 성공 로그에 넣을 샘플 — 청크의 첫/마지막 row만 linkId|statDate로 */
    private String sampleRange(List<AthenaBatchRunner.DailyStatRow> chunk) {
        AthenaBatchRunner.DailyStatRow first = chunk.get(0);
        AthenaBatchRunner.DailyStatRow last = chunk.get(chunk.size() - 1);
        return "first=" + first.linkId() + "|" + first.statDate()
                + ", last=" + last.linkId() + "|" + last.statDate();
    }

    /** dead-letter 로그용 요약 (linkId|statDate 형태로 최대 20개만) */
    private String summarize(List<AthenaBatchRunner.DailyStatRow> rows) {
        return rows.stream()
                .limit(20)
                .map(r -> r.linkId() + "|" + r.statDate())
                .collect(Collectors.joining(", "));
    }

}