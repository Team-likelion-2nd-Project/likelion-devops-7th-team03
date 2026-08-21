package com.example.management.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * K8s CronJob 진입점. "어제(KST) 하루치" 클릭 로그를 Athena로 집계해서
 * link_daily_stats / link_daily_dimension_stats에 UPSERT하고 종료한다.
 *
 * OOM 방지를 위해 대량 쿼리 결과를 전체 List로 적재하지 않고,
 * Athena SDK Paginator 기반 스트리밍(CHUNK_SIZE=1000)으로 처리합니다.
 *
 * 실행: SPRING_PROFILES_ACTIVE=batch, SPRING_MAIN_WEB_APPLICATION_TYPE=none
 */
@Slf4j
@Component
@Profile("batch")
@RequiredArgsConstructor
public class AthenaBatchRunner implements CommandLineRunner {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int CHUNK_SIZE = 1000;

    private final AthenaQueryExecutor queryExecutor;
    private final DailyStatsUpsertRepository dailyStatsRepository;
    private final DimensionStatsUpsertRepository dimensionStatsRepository;
    private final ApplicationContext applicationContext;

    @Value("${app.athena.workgroup}")
    private String workgroup;

    @Value("${app.athena.database:snipy_click_logs}")
    private String database;

    @Override
    public void run(String... args) {
        LocalDate targetDate = args.length > 0
                ? LocalDate.parse(args[0])
                : LocalDate.now(KST).minusDays(1);

        final int exitCode = runBatch(targetDate);

        // CommandLineRunner 종료 후 Spring 컨텍스트 정상 종료 및 Exit Code 반환
        System.exit(SpringApplication.exit(applicationContext, () -> exitCode));
    }

    private int runBatch(LocalDate targetDate) {
        try {
            log.info("Athena 배치 시작. targetDate(KST)={}", targetDate);
            runDailyStats(targetDate);
            runDimensionStats(targetDate);
            log.info("Athena 배치 완료. targetDate(KST)={}", targetDate);
            return 0;
        } catch (Exception e) {
            log.error("Athena 배치 실패. targetDate(KST)={}", targetDate, e);
            return 1;
        }
    }

    private void runDailyStats(LocalDate targetDate) {
        String sql = AthenaQueries.dailyStats(targetDate);

        // 전체 List 대신 청크(1,000건) 단위 스트리밍 소비
        long processedRows = queryExecutor.executeStreaming(
                sql,
                workgroup,
                database,
                CHUNK_SIZE,
                DailyStatRow::from,
                dailyStatsRepository::upsertAll
        );

        log.info("link_daily_stats UPSERT 완료. 총 처리 rows={}", processedRows);
    }

    private void runDimensionStats(LocalDate targetDate) {
        for (String dimensionType : List.of("REFERRER", "DEVICE", "REGION")) {
            String sql = AthenaQueries.dimensionStats(targetDate, dimensionType);

            long processedRows = queryExecutor.executeStreaming(
                    sql,
                    workgroup,
                    database,
                    CHUNK_SIZE,
                    values -> DimensionStatRow.from(values, dimensionType),
                    dimensionStatsRepository::upsertAll
            );

            log.info("link_daily_dimension_stats UPSERT 완료. dimensionType={} 총 처리 rows={}",
                    dimensionType, processedRows);
        }
    }

    /** Athena 결과 한 행 (link_daily_stats용) */
    public record DailyStatRow(Long linkId, LocalDate statDate, int clickCount, int visitorCount) {
        static DailyStatRow from(List<String> values) {
            return new DailyStatRow(
                    Long.parseLong(values.get(0)),
                    LocalDate.parse(values.get(1)),
                    Integer.parseInt(values.get(2)),
                    Integer.parseInt(values.get(3))
            );
        }
    }

    /** Athena 결과 한 행 (link_daily_dimension_stats용) */
    public record DimensionStatRow(
            Long linkId, LocalDate statDate, String dimensionType, String dimensionValue, int clickCount) {
        static DimensionStatRow from(List<String> values, String dimensionType) {
            return new DimensionStatRow(
                    Long.parseLong(values.get(0)),
                    LocalDate.parse(values.get(1)),
                    dimensionType,
                    values.get(2),
                    Integer.parseInt(values.get(3))
            );
        }
    }
}