package com.example.management.stats.service;

import com.example.management.stats.domain.LinkDailyDimensionStat;
import com.example.management.stats.domain.LinkDailyStat;
import com.example.management.stats.dto.DailyChangeResponse;
import com.example.management.stats.dto.DimensionBreakdownResponse;
import com.example.management.stats.repository.LinkDailyDimensionStatRepository;
import com.example.management.stats.repository.LinkDailyStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final LinkDailyStatRepository linkDailyStatRepository;
    private final LinkDailyDimensionStatRepository dimensionStatRepository;

    /**
     * 일별 증감률 조회 (FR-004-2)
     * baseDate = "어제" 날짜를 기준으로, 그 전날(그제)과 비교한다.
     * (오늘 값은 배치 집계 전이라 이 테이블엔 아직 없음 — 실시간 값이 필요하면 Redis에서 별도 조회)
     */
    public DailyChangeResponse getDailyChange(Long linkId, LocalDate baseDate) {
        LocalDate previousDate = baseDate.minusDays(1);

        LinkDailyStat base = linkDailyStatRepository
                .findByLinkIdAndStatDate(linkId, baseDate)
                .orElse(zeroStat(linkId, baseDate));

        LinkDailyStat previous = linkDailyStatRepository
                .findByLinkIdAndStatDate(linkId, previousDate)
                .orElse(zeroStat(linkId, previousDate));

        return new DailyChangeResponse(
                linkId,
                baseDate,
                DailyChangeResponse.MetricChange.of(base.getClickCount(), previous.getClickCount()),
                DailyChangeResponse.MetricChange.of(base.getVisitorCount(), previous.getVisitorCount())
        );
    }

    /**
     * 기타 분포 조회 (FR-004-4 유입경로 / FR-004-5 디바이스·지역)
     * dimensionType: REFERRER / DEVICE / REGION
     */
    public DimensionBreakdownResponse getBreakdown(
            Long linkId,
            LinkDailyDimensionStat.DimensionType dimensionType,
            LocalDate from,
            LocalDate to
    ) {
        List<LinkDailyDimensionStat> rows = dimensionStatRepository
                .findByLinkIdAndTypeAndDateRange(linkId, dimensionType, from, to);

        // 같은 dimensionValue가 여러 날짜에 걸쳐 있을 수 있으니 합산
        var grouped = rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        LinkDailyDimensionStat::getDimensionValue,
                        java.util.stream.Collectors.summingInt(LinkDailyDimensionStat::getClickCount)
                ));

        int total = grouped.values().stream().mapToInt(Integer::intValue).sum();

        List<DimensionBreakdownResponse.Item> items = grouped.entrySet().stream()
                .map(e -> new DimensionBreakdownResponse.Item(
                        e.getKey(),
                        e.getValue(),
                        total == 0 ? 0.0 : Math.round((e.getValue() * 1000.0 / total)) / 10.0
                ))
                .sorted((a, b) -> b.clickCount() - a.clickCount())
                .toList();

        return new DimensionBreakdownResponse(linkId, dimensionType.name(), items);
    }

    private LinkDailyStat zeroStat(Long linkId, LocalDate date) {
        return LinkDailyStat.builder()
                .linkId(linkId)
                .statDate(date)
                .clickCount(0)
                .visitorCount(0)
                .build();
    }
}