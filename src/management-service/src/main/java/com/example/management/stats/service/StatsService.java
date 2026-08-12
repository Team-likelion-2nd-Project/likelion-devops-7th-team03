package com.example.management.stats.service;

import com.example.management.stats.domain.LinkDailyDimensionStat;
import com.example.management.stats.domain.LinkDailyStat;
import com.example.management.stats.dto.DailyChangeResponse;
import com.example.management.stats.dto.DimensionBreakdownResponse;
import com.example.management.stats.dto.StatsResponses.*;
import com.example.management.stats.repository.LinkDailyDimensionStatRepository;
import com.example.management.stats.repository.LinkDailyStatRepository;
import com.example.management.url_link.domain.Link;
import com.example.management.url_link.domain.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatsService {
    // TODO: kakao-login merge 후 SecurityContext에서 실제 userId(UUID)를 꺼내
    // UserRepository로 내부 id 조회하는 방식으로 교체할 것. 지금은 임시 고정값.
    private static final Long TEMP_USER_ID = 1L;

    private final LinkRepository linkRepository;
    private final LinkDailyStatRepository dailyStatRepository;
    private final LinkDailyDimensionStatRepository dimensionStatRepository;

    private final StringRedisTemplate redisTemplate;

    public DailyStatsResponse getDailyStats(String linkUuid, LocalDate from, LocalDate to) {
        Link link = resolveOwnedLink(linkUuid);

        List<DailyStat> daily = dailyStatRepository.findDailyFilled(link.getId(), from, to).stream()
                .map(r -> new DailyStat(r.getStatDate(), r.getClickCount(), r.getVisitorCount()))
                .toList();

        var s = dailyStatRepository.findSummary(link.getId(), from, to);
        DailySummary summary = new DailySummary(
                s.getTotalClicks(), s.getSumOfDailyVisitors(),
                s.getAvgDailyClicks() == null ? 0.0 : s.getAvgDailyClicks(),
                s.getPeakClicks(), s.getActiveDays());

        return new DailyStatsResponse(daily, summary);
    }

    public List<LinkCompare> compareLinks(List<String> linkUuids, LocalDate from, LocalDate to) {
        if (linkUuids == null || linkUuids.isEmpty()) {
            throw new IllegalArgumentException("linkIds must not be empty");
        }

        List<Link> links = linkRepository.findByLinkIdInAndUserIdAndIsVisibleTrue(linkUuids, TEMP_USER_ID);
        List<Long> internalIds = links.stream().map(Link::getId).toList();

        if (internalIds.isEmpty()) {
            return List.of();
        }

        return dailyStatRepository.compareLinks(internalIds, from, to).stream()
                .map(r -> new LinkCompare(r.getLinkUuid(), r.getSlug(), r.getTitle(),
                        r.getTotalClicks(), r.getSumOfDailyVisitors(), r.getPeakDailyClicks()))
                .toList();
    }

    public List<ReferrerStat> getReferrerStats(String linkUuid, LocalDate from, LocalDate to) {
        Link link = resolveOwnedLink(linkUuid);

        return dimensionStatRepository.findReferrerStats(link.getId(), from, to).stream()
                .map(r -> new ReferrerStat(r.getReferrerCategory(), r.getClickCount(), r.getPercentage()))
                .toList();
    }

    // ===================================================================
    // 신규 추가 (일별 증감률 / 기타 분포(디바이스·지역) / 실시간 접속자 수)
    // ===================================================================

    /**
     * 일별 증감률 조회 (FR-004-2)
     * baseDate(기본값: 어제) vs 그 전날을 비교한다.
     * (당일 값은 배치 집계 전이라 이 테이블엔 없음 — 필요하면 realtime API로 별도 조회)
     */
    public DailyChangeResponse getDailyChange(String linkUuid, LocalDate baseDate) {
        Link link = resolveOwnedLink(linkUuid);
        LocalDate previousDate = baseDate.minusDays(1);

        Optional<LinkDailyStat> base = dailyStatRepository.findByLinkIdAndStatDate(link.getId(), baseDate);
        Optional<LinkDailyStat> previous = dailyStatRepository.findByLinkIdAndStatDate(link.getId(), previousDate);

        int baseClicks = base.map(LinkDailyStat::getClickCount).orElse(0);
        int prevClicks = previous.map(LinkDailyStat::getClickCount).orElse(0);
        int baseVisitors = base.map(LinkDailyStat::getVisitorCount).orElse(0);
        int prevVisitors = previous.map(LinkDailyStat::getVisitorCount).orElse(0);

        return new DailyChangeResponse(
                linkUuid,
                baseDate,
                DailyChangeResponse.MetricChange.of(baseClicks, prevClicks),
                DailyChangeResponse.MetricChange.of(baseVisitors, prevVisitors)
        );
    }

    /**
     * 기타 분포 조회 (FR-004-5 디바이스·지역)
     *이 메서드는 DEVICE/REGION
     * dimensionType은 Controller에서 enum으로 미리 검증한 뒤 넘어온다
     * (잘못된 값이면 valueOf()에서 즉시 IllegalArgumentException 발생 → 조용히 0건 나오는 것 방지).
     */
    public DimensionBreakdownResponse getBreakdown(
            String linkUuid,
            LinkDailyDimensionStat.DimensionType dimensionType,
            LocalDate from,
            LocalDate to
    ) {
        Link link = resolveOwnedLink(linkUuid);

        var rows = dimensionStatRepository.findBreakdown(link.getId(), dimensionType.name(), from, to);
        long total = rows.stream().mapToLong(r -> r.getClickCount()).sum();

        List<DimensionBreakdownResponse.Item> items = rows.stream()
                .map(r -> new DimensionBreakdownResponse.Item(
                        r.getDimensionValue(),
                        r.getClickCount().intValue(),
                        total == 0 ? 0.0 : Math.round(r.getClickCount() * 1000.0 / total) / 10.0
                ))
                .toList();

        return new DimensionBreakdownResponse(linkUuid, dimensionType.name(), items);
    }

    /**
     * 실시간 접속자(클릭) 수 조회 — DB를 전혀 거치지 않고 Redis만 조회한다.
     * redirector가 클릭마다 INCR로 올려둔 값을 그대로 읽기만 함.
     * (키 형식: click_count:{내부 link_id}:{date})
     * 소유권 검증은 여기서도 동일하게 resolveOwnedLink()로 처리한다
     * (링크 id만 알면 남의 링크 실시간 클릭수를 볼 수 있는 문제를 막기 위함).
     */
    public long getRealtimeClickCount(String linkUuid) {
        Link link = resolveOwnedLink(linkUuid);
        String key = "click_count:" + link.getId() + ":" + LocalDate.now();
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    private Link resolveOwnedLink(String linkUuid) {
        return linkRepository.findByLinkIdAndUserIdAndIsVisibleTrue(linkUuid, TEMP_USER_ID)
                .orElseThrow(() -> new IllegalArgumentException("link not found or not owned: " + linkUuid));
    }
}
