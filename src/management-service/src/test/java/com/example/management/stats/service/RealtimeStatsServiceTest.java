package com.example.management.stats.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * RealtimeStatsService 단위 테스트.
 * 실제 Redis 서버를 띄우지 않고, StringRedisTemplate을 Mock으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class RealtimeStatsServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private RealtimeStatsService realtimeStatsService;

    @Test
    @DisplayName("Redis에 값이 있으면 그 값을 그대로 반환한다")
    void returnsValueWhenKeyExists() {
        Long linkId = 1L;
        String expectedKey = "click_count:" + linkId + ":" + LocalDate.now();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn("42");

        long result = realtimeStatsService.getRealtimeClickCount(linkId);

        assertThat(result).isEqualTo(42L);
    }

    @Test
    @DisplayName("Redis에 값이 없으면(아직 클릭이 없는 링크) 0을 반환한다")
    void returnsZeroWhenKeyMissing() {
        Long linkId = 999L;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);

        long result = realtimeStatsService.getRealtimeClickCount(linkId);

        assertThat(result).isZero();
    }
}