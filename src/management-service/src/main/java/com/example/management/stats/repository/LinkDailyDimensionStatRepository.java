package com.example.management.stats.repository;

import com.example.management.stats.domain.LinkDailyDimensionStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LinkDailyDimensionStatRepository extends JpaRepository<LinkDailyDimensionStat, Long> {

    /**
     * 기타 분포 조회 (FR-004-5 디바이스·지역)
     * dimensionType으로 REFERRER / DEVICE / REGION 중 하나를 넘겨서 사용.
     */
    @Query("""
        SELECT d FROM LinkDailyDimensionStat d
        WHERE d.linkId = :linkId
          AND d.dimensionType = :dimensionType
          AND d.statDate BETWEEN :from AND :to
        """)
    List<LinkDailyDimensionStat> findByLinkIdAndTypeAndDateRange(
            @Param("linkId") Long linkId,
            @Param("dimensionType") LinkDailyDimensionStat.DimensionType dimensionType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}
