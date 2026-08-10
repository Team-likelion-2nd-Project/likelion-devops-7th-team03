package com.example.management.stats.controller;

import com.example.management.stats.domain.LinkDailyDimensionStat;
import com.example.management.stats.dto.DailyChangeResponse;
import com.example.management.stats.dto.DimensionBreakdownResponse;
import com.example.management.stats.service.RealtimeStatsService;
import com.example.management.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/links/{linkId}/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final RealtimeStatsService realtimeStatsService;

    // TODO: linkId는 지금 links.id(내부 BIGINT)를 그대로 받는 임시 버전.
    //       실제로는 API 경로의 외부 노출용 UUID(link_id)를 서비스 계층에서 내부 id로 변환해야 함.
    //       + 소유권 검증(로그인한 userId와 links.user_id 일치 확인)도 아직 없음 — 인증 미들웨어 완성 후 추가.

    /** 일별 증감률 조회 (FR-004-2) */
    @GetMapping("/daily-change")
    public DailyChangeResponse getDailyChange(
            @PathVariable Long linkId,
            @RequestParam(required = false) LocalDate baseDate
    ) {
        LocalDate target = (baseDate != null) ? baseDate : LocalDate.now().minusDays(1);
        return statsService.getDailyChange(linkId, target);
    }

    /** 기타 분포 조회 (FR-004-4 유입경로 / FR-004-5 디바이스·지역) */
    @GetMapping("/breakdown")
    public DimensionBreakdownResponse getBreakdown(
            @PathVariable Long linkId,
            @RequestParam String type,           // REFERRER / DEVICE / REGION
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        LinkDailyDimensionStat.DimensionType dimensionType =
                LinkDailyDimensionStat.DimensionType.valueOf(type.toUpperCase());
        return statsService.getBreakdown(linkId, dimensionType, from, to);
    }

    /** 실시간 접속자(클릭) 수 조회 — Redis만 조회, DB 안 거침 */
    @GetMapping("/realtime")
    public Map<String, Object> getRealtimeCount(@PathVariable Long linkId) {
        long count = realtimeStatsService.getRealtimeClickCount(linkId);
        return Map.of("linkId", linkId, "realtimeClickCount", count);
    }
}