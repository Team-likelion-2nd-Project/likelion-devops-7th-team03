package com.example.management.statics.service;

import com.example.management.stats.domain.LinkDailyDimensionStat;
import com.example.management.stats.domain.LinkDailyStat;
import com.example.management.stats.dto.DailyChangeResponse;
import com.example.management.stats.dto.DimensionBreakdownResponse;
import com.example.management.stats.repository.LinkDailyDimensionStatRepository;
import com.example.management.stats.repository.LinkDailyStatRepository;
import com.example.management.stats.service.StatsService;
import com.example.management.links.domain.Link;
import com.example.management.links.repository.LinkRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * StatsService 단위 테스트.
 * DB/Redis를 실제로 띄우지 않고 Repository/RedisTemplate을 Mock으로 대체해서
 * "계산 로직 + 소유권 검증 흐름"만 검증한다.
 *
 * TEMP_USER_ID(=1L)는 StatsService 내부의 임시 고정값과 반드시 일치시켜야 한다
 * (kakao-login 완성 후 SecurityContext 기반으로 교체될 예정 — StatsService의 TODO 참조).
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final Long TEMP_USER_ID = 1L;
    private static final String LINK_UUID = "550e8400-e29b-41d4-a716-446655440000";
    private static final Long INTERNAL_LINK_ID = 10L;

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private LinkDailyStatRepository dailyStatRepository;

    @Mock
    private LinkDailyDimensionStatRepository dimensionStatRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private StatsService statsService;

    /** 소유권 검증(resolveOwnedLink)이 성공하는 상황을 공통으로 세팅 */
    private void mockOwnedLink() {
        Link mockLink = Mockito.mock(Link.class);
        when(mockLink.getId()).thenReturn(INTERNAL_LINK_ID);
        when(linkRepository.findByLinkIdAndUserIdAndIsVisibleTrue(LINK_UUID, TEMP_USER_ID))
                .thenReturn(Optional.of(mockLink));
    }

    @Nested
    @DisplayName("일별 증감률 조회")
    class DailyChange {

        @Test
        @DisplayName("어제보다 클릭이 늘었으면 양수 증감률이 나온다")
        void increasedClicks_returnsPositiveRate() {
            // given
            mockOwnedLink();
            LocalDate yesterday = LocalDate.of(2026, 8, 6);
            LocalDate dayBeforeYesterday = LocalDate.of(2026, 8, 5);

            when(dailyStatRepository.findByLinkIdAndStatDate(INTERNAL_LINK_ID, yesterday))
                    .thenReturn(Optional.of(stat(yesterday, 120, 95)));
            when(dailyStatRepository.findByLinkIdAndStatDate(INTERNAL_LINK_ID, dayBeforeYesterday))
                    .thenReturn(Optional.of(stat(dayBeforeYesterday, 100, 80)));

            // when
            DailyChangeResponse response = statsService.getDailyChange(LINK_UUID, yesterday);

            log.info("===== 일별 증감률 테스트 (증가 케이스) =====");
            log.info("linkId={}, baseDate={}", response.linkId(), response.baseDate());
            log.info("clicks: base={}, previous={}, changeRate={}%",
                    response.clicks().base(), response.clicks().previous(), response.clicks().changeRate());
            log.info("visitors: base={}, previous={}, changeRate={}%",
                    response.visitors().base(), response.visitors().previous(), response.visitors().changeRate());

            // then — (120-100)/100*100 = 20.0%
            assertThat(response.linkId()).isEqualTo(LINK_UUID);
            assertThat(response.clicks().changeRate()).isEqualTo(20.0);
            assertThat(response.clicks().base()).isEqualTo(120);
            assertThat(response.clicks().previous()).isEqualTo(100);
        }

        @Test
        @DisplayName("비교 대상 날짜 데이터가 없으면 0으로 취급해 계산한다")
        void missingPreviousData_treatedAsZero() {
            mockOwnedLink();
            LocalDate yesterday = LocalDate.of(2026, 8, 6);
            LocalDate dayBeforeYesterday = LocalDate.of(2026, 8, 5);

            when(dailyStatRepository.findByLinkIdAndStatDate(INTERNAL_LINK_ID, yesterday))
                    .thenReturn(Optional.of(stat(yesterday, 50, 40)));
            when(dailyStatRepository.findByLinkIdAndStatDate(INTERNAL_LINK_ID, dayBeforeYesterday))
                    .thenReturn(Optional.empty());

            DailyChangeResponse response = statsService.getDailyChange(LINK_UUID, yesterday);

            log.info("===== 일별 증감률 테스트 (그제 데이터 없음) =====");
            log.info("clicks: base={}, previous={}, changeRate={}%",
                    response.clicks().base(), response.clicks().previous(), response.clicks().changeRate());

            // 그제 데이터가 없어(0건) 클릭이 0에서 50으로 늘었으니 100%로 처리
            assertThat(response.clicks().changeRate()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("양쪽 다 0이면 증감률도 0이다 (0으로 나누기 방지)")
        void bothZero_rateIsZero() {
            mockOwnedLink();
            LocalDate yesterday = LocalDate.of(2026, 8, 6);
            LocalDate dayBeforeYesterday = LocalDate.of(2026, 8, 5);

            when(dailyStatRepository.findByLinkIdAndStatDate(INTERNAL_LINK_ID, yesterday))
                    .thenReturn(Optional.of(stat(yesterday, 0, 0)));
            when(dailyStatRepository.findByLinkIdAndStatDate(INTERNAL_LINK_ID, dayBeforeYesterday))
                    .thenReturn(Optional.of(stat(dayBeforeYesterday, 0, 0)));

            DailyChangeResponse response = statsService.getDailyChange(LINK_UUID, yesterday);

            log.info("===== 일별 증감률 테스트 (양쪽 0) =====");
            log.info("changeRate={}%", response.clicks().changeRate());

            assertThat(response.clicks().changeRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("소유하지 않은/존재하지 않는 링크면 IllegalArgumentException이 발생한다")
        void notOwnedLink_throwsException() {
            when(linkRepository.findByLinkIdAndUserIdAndIsVisibleTrue(anyString(), eq(TEMP_USER_ID)))
                    .thenReturn(Optional.empty());

            log.info("===== 소유권 검증 실패 테스트 =====");

            assertThatThrownBy(() -> statsService.getDailyChange("not-owned-uuid", LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not-owned-uuid");
        }
    }

    @Nested
    @DisplayName("분포 조회")
    class Breakdown {

        @Test
        @DisplayName("같은 값이 여러 날짜에 걸쳐 있으면 합산해서 비율을 계산한다")
        void aggregatesAcrossDatesAndCalculatesPercentage() {
            mockOwnedLink();
            LocalDate from = LocalDate.of(2026, 8, 1);
            LocalDate to = LocalDate.of(2026, 8, 7);

            when(dimensionStatRepository.findBreakdown(
                    eq(INTERNAL_LINK_ID), eq("DEVICE"), eq(from), eq(to)))
                    .thenReturn(List.of(
                            breakdownRow("MOBILE", 100L),
                            breakdownRow("DESKTOP", 100L)
                    ));

            DimensionBreakdownResponse response = statsService.getBreakdown(
                    LINK_UUID, LinkDailyDimensionStat.DimensionType.DEVICE, from, to);

            log.info("===== 분포 조회 테스트 =====");
            response.breakdown().forEach(item ->
                    log.info("value={}, clickCount={}, percentage={}%",
                            item.value(), item.clickCount(), item.percentage()));

            // MOBILE 100건, DESKTOP 100건 → 총 200건, 각각 50%
            assertThat(response.breakdown()).hasSize(2);
            assertThat(response.breakdown())
                    .extracting(DimensionBreakdownResponse.Item::value, DimensionBreakdownResponse.Item::clickCount)
                    .containsExactlyInAnyOrder(
                            tuple("MOBILE", 100),
                            tuple("DESKTOP", 100)
                    );
            assertThat(response.breakdown().get(0).percentage()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("데이터가 없으면 빈 목록을 반환한다")
        void noData_returnsEmptyList() {
            mockOwnedLink();
            LocalDate from = LocalDate.of(2026, 8, 1);
            LocalDate to = LocalDate.of(2026, 8, 7);

            when(dimensionStatRepository.findBreakdown(any(), any(), any(), any()))
                    .thenReturn(List.of());

            DimensionBreakdownResponse response = statsService.getBreakdown(
                    LINK_UUID, LinkDailyDimensionStat.DimensionType.REGION, from, to);

            log.info("===== 분포 조회 테스트 (데이터 없음) =====");
            log.info("breakdown size={}", response.breakdown().size());

            assertThat(response.breakdown()).isEmpty();
        }
    }

    @Nested
    @DisplayName("실시간 접속자 수 조회")
    class Realtime {

        @Test
        @DisplayName("Redis에 값이 있으면 그 값을 그대로 반환한다 (내부 id 기준 키 사용)")
        void returnsValueWhenKeyExists() {
            mockOwnedLink();
            String expectedKey = "click_count:" + INTERNAL_LINK_ID + ":" + LocalDate.now();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(expectedKey)).thenReturn("42");

            long result = statsService.getRealtimeClickCount(LINK_UUID);

            log.info("===== 실시간 접속자 수 테스트 (값 있음) =====");
            log.info("redisKey={}, result={}", expectedKey, result);

            assertThat(result).isEqualTo(42L);
        }

        @Test
        @DisplayName("Redis에 값이 없으면(아직 클릭이 없는 링크) 0을 반환한다")
        void returnsZeroWhenKeyMissing() {
            mockOwnedLink();

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);

            long result = statsService.getRealtimeClickCount(LINK_UUID);

            log.info("===== 실시간 접속자 수 테스트 (값 없음) =====");
            log.info("result={}", result);

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("소유하지 않은 링크면 Redis 조회 전에 예외가 발생한다")
        void notOwnedLink_throwsBeforeRedisLookup() {
            when(linkRepository.findByLinkIdAndUserIdAndIsVisibleTrue(anyString(), eq(TEMP_USER_ID)))
                    .thenReturn(Optional.empty());

            log.info("===== 실시간 접속자 수 - 소유권 검증 실패 테스트 =====");

            assertThatThrownBy(() -> statsService.getRealtimeClickCount("someone-elses-link"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ===================================================================
    // 헬퍼
    // ===================================================================

    private LinkDailyStat stat(LocalDate date, int clickCount, int visitorCount) {
        return LinkDailyStat.builder()
                .linkId(INTERNAL_LINK_ID)
                .statDate(date)
                .clickCount(clickCount)
                .visitorCount(visitorCount)
                .build();
    }

    private LinkDailyDimensionStatRepository.BreakdownRow breakdownRow(String value, Long clickCount) {
        return new LinkDailyDimensionStatRepository.BreakdownRow() {
            @Override
            public String getDimensionValue() {
                return value;
            }

            @Override
            public Long getClickCount() {
                return clickCount;
            }
        };
    }
}
