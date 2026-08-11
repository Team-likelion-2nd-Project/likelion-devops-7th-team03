package com.example.management.stats.service;

import com.example.management.stats.controller.dto.StatsResponses.*;
import com.example.management.stats.domain.LinkDailyDimensionStatRepository;
import com.example.management.stats.domain.LinkDailyStatRepository;
import com.example.management.url_link.domain.Link;
import com.example.management.url_link.domain.LinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {
    // TODO: kakao-login merge 후 SecurityContext에서 실제 userId(UUID)를 꺼내
    // UserRepository로 내부 id 조회하는 방식으로 교체할 것. 지금은 임시 고정값.
    private static final Long TEMP_USER_ID = 1L;

    private final LinkRepository linkRepository;
    private final LinkDailyStatRepository dailyStatRepository;
    private final LinkDailyDimensionStatRepository dimensionStatRepository;

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

    private Link resolveOwnedLink(String linkUuid) {
        return linkRepository.findByLinkIdAndUserIdAndIsVisibleTrue(linkUuid, TEMP_USER_ID)
                .orElseThrow(() -> new IllegalArgumentException("link not found or not owned: " + linkUuid));
    }
}
