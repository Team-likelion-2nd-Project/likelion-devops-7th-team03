package com.example.management.batch;

import lombok.RequiredArgsConstructor;
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
 */
@Repository
@RequiredArgsConstructor
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

    @Transactional
    public void upsertAll(List<AthenaBatchRunner.DimensionStatRow> rows) {
        if (rows.isEmpty()) {
            return;
        }

        List<AthenaBatchRunner.DimensionStatRow> effectiveRows =
                "REGION".equals(rows.get(0).dimensionType())
                        ? bucketTopNPerLink(rows)
                        : rows;

        MapSqlParameterSource[] params = effectiveRows.stream()
                .map(row -> new MapSqlParameterSource()
                        .addValue("linkId", row.linkId())
                        .addValue("statDate", row.statDate())
                        .addValue("dimensionType", row.dimensionType())
                        .addValue("dimensionValue", row.dimensionValue())
                        .addValue("clickCount", row.clickCount()))
                .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(UPSERT_SQL, params);
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
