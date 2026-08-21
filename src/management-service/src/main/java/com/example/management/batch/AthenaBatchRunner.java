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
 * 실행: SPRING_PROFILES_ACTIVE=batch, SPRING_MAIN_WEB_APPLICATION_TYPE=none
 * (웹서버 안 띄우고 배치만 돌고 프로세스 종료 — CronJob 컨테이너는 완료돼야 함)
 */
@Slf4j
@Component
@Profile("batch")
@RequiredArgsConstructor
public class AthenaBatchRunner implements CommandLineRunner {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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
        // 인자로 날짜를 넘기면 그 날짜(KST) 기준으로 재집계, 없으면 어제(KST)
        LocalDate targetDate = args.length > 0
                ? LocalDate.parse(args[0])
                : LocalDate.now(KST).minusDays(1);

        final int exitCode = runBatch(targetDate);

        // CommandLineRunner가 끝나도 Spring 컨텍스트가 살아있으면 Job이 안 끝난 것처럼 붙잡힌다.
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
        List<DailyStatRow> rows = queryExecutor.execute(sql, workgroup, database, DailyStatRow::from);
        dailyStatsRepository.upsertAll(rows);
        log.info("link_daily_stats UPSERT 완료. rows={}", rows.size());
    }

    private void runDimensionStats(LocalDate targetDate) {
        for (String dimensionType : List.of("REFERRER", "DEVICE", "REGION")) {
            String sql = AthenaQueries.dimensionStats(targetDate, dimensionType);
            List<DimensionStatRow> rows = queryExecutor.execute(
                    sql, workgroup, database, values -> DimensionStatRow.from(values, dimensionType));
            dimensionStatsRepository.upsertAll(rows);
            log.info("link_daily_dimension_stats UPSERT 완료. dimensionType={} rows={}",
                    dimensionType, rows.size());
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
