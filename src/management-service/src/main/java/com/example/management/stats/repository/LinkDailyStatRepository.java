package com.example.management.stats.repository;

import com.example.management.stats.domain.LinkDailyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LinkDailyStatRepository extends JpaRepository<LinkDailyStat, Long> {

    /**
     * 기간별 클릭/방문자 수 분포 (FR-004-1)
     * 소유권 검증은 서비스 계층에서 links.user_id 확인 후 이 메서드를 호출하는 방식으로 처리.
     */
    @Query("""
        SELECT s FROM LinkDailyStat s
        WHERE s.linkId = :linkId
          AND s.statDate BETWEEN :from AND :to
        ORDER BY s.statDate
        """)
    List<LinkDailyStat> findByLinkIdAndDateRange(
            @Param("linkId") Long linkId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    /** 특정 날짜 단건 조회 (일별 증감률 계산용) */
    Optional<LinkDailyStat> findByLinkIdAndStatDate(Long linkId, LocalDate statDate);

    /** 링크별 비교 (FR-004-3) — 여러 링크의 기간 합계 */
    @Query("""
        SELECT s FROM LinkDailyStat s
        WHERE s.linkId IN :linkIds
          AND s.statDate BETWEEN :from AND :to
        """)
    List<LinkDailyStat> findByLinkIdInAndDateRange(
            @Param("linkIds") List<Long> linkIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}