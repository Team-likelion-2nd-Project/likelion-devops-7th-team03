package com.example.management.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * link_daily_stats에 대량 UPSERT. JPA save-if-not-exists는 링크 수만큼 2N 쿼리가
 * 나가므로(엔티티 주석 참고) 네이티브 INSERT ... ON DUPLICATE KEY UPDATE 배치로 처리한다.
 */
@Repository
@RequiredArgsConstructor
public class DailyStatsUpsertRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO link_daily_stats (link_id, stat_date, click_count, visitor_count)
            VALUES (:linkId, :statDate, :clickCount, :visitorCount)
            ON DUPLICATE KEY UPDATE
                click_count = VALUES(click_count),
                visitor_count = VALUES(visitor_count)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional
    public void upsertAll(List<AthenaBatchRunner.DailyStatRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] params = rows.stream()
                .map(row -> new MapSqlParameterSource()
                        .addValue("linkId", row.linkId())
                        .addValue("statDate", row.statDate())
                        .addValue("clickCount", row.clickCount())
                        .addValue("visitorCount", row.visitorCount()))
                .toArray(MapSqlParameterSource[]::new);

        jdbcTemplate.batchUpdate(UPSERT_SQL, params);
    }
}
