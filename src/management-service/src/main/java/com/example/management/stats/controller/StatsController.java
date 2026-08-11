package com.example.management.stats.controller;

import com.example.management.stats.domain.LinkDailyDimensionStat;
import com.example.management.stats.dto.DailyChangeResponse;
import com.example.management.stats.dto.DimensionBreakdownResponse;
import com.example.management.stats.dto.StatsResponses.*;
import com.example.management.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/{linkId}/stats/daily")
    public DailyStatsResponse getDailyStats(
            @PathVariable String linkId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statsService.getDailyStats(linkId, from, to);
    }

    @GetMapping("/stats/compare")
    public List<LinkCompare> compareLinks(
            @RequestParam List<String> linkIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statsService.compareLinks(linkIds, from, to);
    }

    @GetMapping("/{linkId}/stats/referrers")
    public List<ReferrerStat> getReferrerStats(
            @PathVariable String linkId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statsService.getReferrerStats(linkId, from, to);
    }


    /** 일별 증감률 조회 (FR-004-2) */
    @GetMapping("/{linkId}/stats/daily-change")
    public DailyChangeResponse getDailyChange(
            @PathVariable String linkId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate
    ) {
        LocalDate target = (baseDate != null) ? baseDate : LocalDate.now().minusDays(1);
        return statsService.getDailyChange(linkId, target);
    }

    /** 기타 분포 조회 (FR-004-5 디바이스·지역, REFERRER는 기존 getReferrerStats 사용) */
    @GetMapping("/{linkId}/stats/breakdown")
    public DimensionBreakdownResponse getBreakdown(
            @PathVariable String linkId,
            @RequestParam String type,   // DEVICE / REGION
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LinkDailyDimensionStat.DimensionType dimensionType =
                LinkDailyDimensionStat.DimensionType.valueOf(type.toUpperCase());  // ← 여기서 String → enum 변환
        return statsService.getBreakdown(linkId, dimensionType, from, to);
    }


    /** 실시간 접속자(클릭) 수 조회 — Redis만 조회, DB 안 거침 */
    @GetMapping("/{linkId}/stats/realtime")
    public Map<String, Object> getRealtimeCount(@PathVariable String linkId) {
        long count = statsService.getRealtimeClickCount(linkId);
        return Map.of("linkId", linkId, "realtimeClickCount", count);
    }
}
