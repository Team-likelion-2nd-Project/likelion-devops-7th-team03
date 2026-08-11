package com.example.management.stats.controller;

import com.example.management.stats.controller.dto.StatsResponses.*;
import com.example.management.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

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
}
