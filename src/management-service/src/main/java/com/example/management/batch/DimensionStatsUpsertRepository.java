package com.example.management.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * link_daily_dimension_stats UPSERT.
 * REGION은 값 종류가 많아 행이 불어나므로(엔티티 주석 참고), 링크·일자별 상위
 * TOP_N개만 남기고 나머지는 클릭수를 합산해 'ETC' 한 행으로 압축한다.
 * REFERRER/DEVICE는 종류가 적어 그대로 저장한다.
 *
 * rewriteBatchedStatements=true(JDBC URL)가 batchUpdate 전체를 하나의 거대한
 * 멀티밸류 INSERT 문자열로 rewrite하려고 시도하는데, 부하테스트 등으로 row 수가
 * 급증하면(#40 OOM 사고) 그 rewrite 과정 자체가 heap을 다 잡아먹고 죽는다.
 * chunkSize 단위로 나눠서 배치 실행 자체를 여러 트랜잭션으로 쪼갠다.
 *
 * [재시도 정책]
 * rewriteBatchedStatements=true 때문에 청크 하나가 사실상 멀티밸류 INSERT 한 문장으로
 * 합쳐진다. 즉 그 안의 row 하나만 실패해도 JDBC가 "몇 번째 row 실패"를 알려주지 않고
 * 문(statement) 전체가 롤백된다 → 순수 row 단위 부분 재시도는 신뢰 불가.
 * 그래서:
 *   1) 청크 전체를 최대 maxRetry회 지수 백오프로 재시도 (일시적 에러 대응: 락 타임아웃,
 *      커넥션 순단 등 대부분 여기서 해결됨)
 *   2) 그래도 실패하면 청크를 반으로 쪼개 재귀적으로 재시도(bisect) → 진짜 문제 있는
 *      row만 좁혀서 격리
 *   3) bisectFloor 이하로 좁혀졌는데도 실패하면 그 row(들)는 dead-letter로 로그만
 *      남기고 skip (배치 전체를 죽이지 않음)
 *
 * [서킷 브레이커]
 * row 몇 개가 이상해서 실패하는 거랑, DB 자체가 맛이 가서(커넥션 풀 고갈, DB 다운 등)
 * 실패하는 건 다르게 다뤄야 한다. 후자인데 위 재시도/bisect 로직만 있으면 남은 수천 개
 * 청크를 전부 (3회 재시도 + bisect) 하느라 잡이 끝도 없이 돌면서 시간만 날린다.
 * → "청크가 연속으로 N번 실패"하면 (row 몇 개 문제가 아니라 시스템 문제라고 판단)
 *   즉시 배치 전체를 중단시킨다. 청크가 하나라도 성공하면 연속 카운트는 리셋.
 */
@Slf4j
@Repository
public class DimensionStatsUpsertRepository {

    private static final int REGION_TOP_N = 10;
    private static final String ETC = "ETC";

    private static final String UPSERT_SQL = """
            INSERT INTO link_daily_dimension_stats
                (link_id, stat_date, dimension_type, dimension_value, click_count)
            VALUES (:linkId, :statDate, :dimensionType, :dimensionValue, :clickCount)
            ON DUPLICATE KEY UPDATE
                click_count = VALUES(click_count)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // 아래 다섯 개는 원래 static final 상수였는데, 테스트에서 작은 값으로 주입할 수 있도록
    // 인스턴스 필드로 뺐다. 운영 코드는 기본 생성자(@Autowired)로 아래 기본값 그대로 쓴다.
    private final int chunkSize;
    private final int maxRetry;
    private final long baseBackoffMs;
    private final int bisectFloor;
    private final int circuitBreakerThreshold;
    private final Sleeper sleeper;

    @Autowired
    public DimensionStatsUpsertRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, 1000, 3, 200L, 5, 3, DEFAULT_SLEEPER);
    }

    /** 테스트 전용 생성자. 작은 chunkSize/bisectFloor와 no-op Sleeper를 주입해 빠르게 검증한다. */
    DimensionStatsUpsertRepository(NamedParameterJdbcTemplate jdbcTemplate, int chunkSize, int maxRetry,
                                   long baseBackoffMs, int bisectFloor, int circuitBreakerThreshold, Sleeper sleeper) {
        this.jdbcTemplate = jdbcTemplate;
        this.chunkSize = chunkSize;
        this.maxRetry = maxRetry;
        this.baseBackoffMs = baseBackoffMs;
        this.bisectFloor = bisectFloor;
        this.circuitBreakerThreshold = circuitBreakerThreshold;
        this.sleeper = sleeper;
    }

    private static final Sleeper DEFAULT_SLEEPER = millis -> {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 대기 중 인터럽트", ie);
        }
    };

    /** 재시도 백오프 대기를 추상화. 운영에선 진짜 sleep, 테스트에선 no-op으로 주입해서 빠르게 돌린다. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis);
    }

    public void upsertAll(List<AthenaBatchRunner.DimensionStatRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        List<AthenaBatchRunner.DimensionStatRow> effectiveRows =
                "REGION".equals(rows.get(0).dimensionType())
                        ? bucketTopNPerLink(rows)
                        : rows;

        int totalChunks = (effectiveRows.size() + chunkSize - 1) / chunkSize;
        int chunkIndex = 0;
        int failedRowCount = 0;
        int consecutiveFailedChunks = 0;

        for (int i = 0; i < effectiveRows.size(); i += chunkSize) {
            chunkIndex++;
            List<AthenaBatchRunner.DimensionStatRow> chunk =
                    effectiveRows.subList(i, Math.min(i + chunkSize, effectiveRows.size()));

            log.info("[dimension-upsert] chunk {}/{} 시작. size={}", chunkIndex, totalChunks, chunk.size());
            long startedAt = System.currentTimeMillis();

            List<AthenaBatchRunner.DimensionStatRow> deadLetters = upsertWithRetry(chunk, chunkIndex, 1);
            long elapsedMs = System.currentTimeMillis() - startedAt;

            if (deadLetters.isEmpty()) {
                log.info("[dimension-upsert] chunk {}/{} 완료. size={}, elapsedMs={}, sample={}",
                        chunkIndex, totalChunks, chunk.size(), elapsedMs, sampleRange(chunk));
                consecutiveFailedChunks = 0;
            } else {
                failedRowCount += deadLetters.size();
                consecutiveFailedChunks++;
                log.error("[dimension-upsert] chunk {}/{} 일부 실패. size={}, deadLetters={}, rows={}, consecutiveFailedChunks={}",
                        chunkIndex, totalChunks, chunk.size(), deadLetters.size(), summarize(deadLetters), consecutiveFailedChunks);

                if (consecutiveFailedChunks >= circuitBreakerThreshold) {
                    log.error("[dimension-upsert] 서킷 브레이커 작동: 청크 {}회 연속 실패 → 배치 중단. " +
                                    "dimensionType={}, 처리된 청크={}/{}, 지금까지 실패 row={}",
                            consecutiveFailedChunks, rows.get(0).dimensionType(), chunkIndex, totalChunks, failedRowCount);
                    throw new BatchCircuitBreakerException(
                            "청크 " + consecutiveFailedChunks + "회 연속 실패로 배치 중단 (chunk "
                                    + chunkIndex + "/" + totalChunks + ")");
                }
            }
        }

        log.info("link_daily_dimension_stats 전체 UPSERT 완료. dimensionType={}, rawRows={}, effectiveRows={}, chunkSize={}, failedRows={}",
                rows.get(0).dimensionType(), rows.size(), effectiveRows.size(), chunkSize, failedRowCount);
    }

    /**
     * 청크를 최대 maxRetry회 재시도. 그래도 실패하면 bisect(반으로 쪼개서 재귀 재시도)로
     * 문제 row를 좁혀나간다. 최종적으로 실패한(=dead-letter) row 목록을 반환한다.
     */
    private List<AthenaBatchRunner.DimensionStatRow> upsertWithRetry(
            List<AthenaBatchRunner.DimensionStatRow> chunk, int chunkIndex, int depth) {

        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                upsertChunk(chunk);
                if (attempt > 1) {
                    log.warn("[dimension-upsert] chunk {} (depth={}) {}번째 시도에 성공. size={}",
                            chunkIndex, depth, attempt, chunk.size());
                }
                return List.of();
            } catch (DataAccessException e) {
                log.warn("[dimension-upsert] chunk {} (depth={}) {}번째 시도 실패. size={}, cause={}",
                        chunkIndex, depth, attempt, chunk.size(), e.getMessage());

                if (attempt == maxRetry) {
                    break;
                }
                sleepBackoff(attempt);
            }
        }

        // 여기까지 왔다는 건 maxRetry 다 실패했다는 뜻 → bisect 시도
        if (chunk.size() <= bisectFloor) {
            log.error("[dimension-upsert] chunk {} (depth={}) bisectFloor({}) 이하로도 실패 → dead-letter 처리. size={}",
                    chunkIndex, depth, bisectFloor, chunk.size());
            return chunk;
        }

        log.warn("[dimension-upsert] chunk {} (depth={}) 재시도 소진 → bisect 진행. size={}",
                chunkIndex, depth, chunk.size());

        int mid = chunk.size() / 2;
        List<AthenaBatchRunner.DimensionStatRow> left = chunk.subList(0, mid);
        List<AthenaBatchRunner.DimensionStatRow> right = chunk.subList(mid, chunk.size());

        List<AthenaBatchRunner.DimensionStatRow> deadLetters = new ArrayList<>();
        deadLetters.addAll(upsertWithRetry(left, chunkIndex, depth + 1));
        deadLetters.addAll(upsertWithRetry(right, chunkIndex, depth + 1));
        return deadLetters;
    }

    private void sleepBackoff(int attempt) {
        long backoffMs = baseBackoffMs * (1L << (attempt - 1)); // 200ms, 400ms, 800ms...
        sleeper.sleep(backoffMs);
    }

    @Transactional
    protected void upsertChunk(List<AthenaBatchRunner.DimensionStatRow> chunk) {
        MapSqlParameterSource[] params = chunk.stream()
                .map(row -> new MapSqlParameterSource()
                        .addValue("linkId", row.linkId())
                        .addValue("statDate", row.statDate())
                        .addValue("dimensionType", row.dimensionType())
                        .addValue("dimensionValue", row.dimensionValue())
                        .addValue("clickCount", row.clickCount()))
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
            log.debug("[dimension-upsert] batchUpdate 결과: inserted={}, updated={}, unchanged={}",
                    inserted, updated, unchanged);
        }
    }

    /** 성공 로그에 넣을 샘플 — 청크의 첫/마지막 row만 linkId|statDate|dimensionValue로 */
    private String sampleRange(List<AthenaBatchRunner.DimensionStatRow> chunk) {
        AthenaBatchRunner.DimensionStatRow first = chunk.get(0);
        AthenaBatchRunner.DimensionStatRow last = chunk.get(chunk.size() - 1);
        return "first=" + first.linkId() + "|" + first.statDate() + "|" + first.dimensionValue()
                + ", last=" + last.linkId() + "|" + last.statDate() + "|" + last.dimensionValue();
    }

    /** dead-letter 로그용 요약 (linkId|statDate|dimensionValue 형태로 최대 20개만) */
    private String summarize(List<AthenaBatchRunner.DimensionStatRow> rows) {
        return rows.stream()
                .limit(20)
                .map(r -> r.linkId() + "|" + r.statDate() + "|" + r.dimensionValue())
                .collect(Collectors.joining(", "));
    }

    /** (linkId, statDate) 그룹마다 클릭수 상위 REGION_TOP_N개만 남기고 나머지는 ETC로 합산 */
    private List<AthenaBatchRunner.DimensionStatRow> bucketTopNPerLink(
            List<AthenaBatchRunner.DimensionStatRow> rows) {

        Map<String, List<AthenaBatchRunner.DimensionStatRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(r -> r.linkId() + "|" + r.statDate()));

        List<AthenaBatchRunner.DimensionStatRow> result = new ArrayList<>();
        for (List<AthenaBatchRunner.DimensionStatRow> group : grouped.values()) {
            List<AthenaBatchRunner.DimensionStatRow> sorted = group.stream()
                    .sorted(Comparator.comparingInt(AthenaBatchRunner.DimensionStatRow::clickCount).reversed())
                    .toList();

            result.addAll(sorted.subList(0, Math.min(REGION_TOP_N, sorted.size())));

            if (sorted.size() > REGION_TOP_N) {
                List<AthenaBatchRunner.DimensionStatRow> rest = sorted.subList(REGION_TOP_N, sorted.size());
                int etcTotal = rest.stream().mapToInt(AthenaBatchRunner.DimensionStatRow::clickCount).sum();
                AthenaBatchRunner.DimensionStatRow first = rest.get(0);
                result.add(new AthenaBatchRunner.DimensionStatRow(
                        first.linkId(), first.statDate(), first.dimensionType(), ETC, etcTotal));
            }
        }
        return result;
    }
}