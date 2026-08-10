package com.example.management.stats.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 실시간 접속자 수 — DB를 전혀 거치지 않고 Redis만 조회한다.
 * redirector가 클릭마다 INCR로 올려둔 값을 그대로 읽기만 함.
 * (키 형식: click_count:{link_id}:{date})
 */
@Service
@RequiredArgsConstructor
public class RealtimeStatsService {

    private final StringRedisTemplate redisTemplate;

    public long getRealtimeClickCount(Long linkId) {
        String key = "click_count:" + linkId + ":" + LocalDate.now();
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }
}
