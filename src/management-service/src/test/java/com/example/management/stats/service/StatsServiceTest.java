package com.example.management.stats.service;

import com.example.management.stats.domain.LinkDailyDimensionStat;
import com.example.management.stats.domain.LinkDailyStat;
import com.example.management.stats.dto.DailyChangeResponse;
import com.example.management.stats.dto.DimensionBreakdownResponse;
import com.example.management.stats.repository.LinkDailyDimensionStatRepository;
import com.example.management.stats.repository.LinkDailyStatRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * StatsService 단위 테스트.
 * DB/Redis를 실제로 띄우지 않고, Repository를 가짜(Mock)로 대체해서
 * "계산 로직 자체"만 검증한다. (증감률 공식, 분포 비율 계산 등)
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    @Mock
    private LinkDailyStatRepository linkDailyStatRepository;

    @Mock
    private LinkDailyDimensionStatRepository dimensionStatRepository;

    @InjectMocks
    private StatsService statsService;

    @Nested
    @DisplayName("일별 증감률 조회")
    class DailyChange {

        @Test
        @DisplayName("어제보다 클릭이 늘었으면 양수 증감률이 나온다")
        void increasedClicks_returnsPositiveRate() {
            // given
            Long linkId = 1L;
            LocalDate yesterday = LocalDate.of(2026, 8, 6);
            LocalDate dayBeforeYesterday = LocalDate.of(2026, 8, 5);

            when(linkDailyStatRepository.findByLinkIdAndStatDate(linkId, yesterday))
                    .thenReturn(Optional.of(stat(linkId, yesterday, 120, 95)));
            when(linkDailyStatRepository.findByLinkIdAndStatDate(linkId, dayBeforeYesterday))
                    .thenReturn(Optional.of(stat(linkId, dayBeforeYesterday, 100, 80)));

            // when
            DailyChangeResponse response = statsService.getDailyChange(linkId, yesterday);

            log.info("===== 일별 증감률 테스트 =====");
            log.info("현재 클릭 수: {}", response.clicks().base());
            log.info("이전 클릭 수: {}", response.clicks().previous());
            log.info("증감률: {}%", response.clicks().changeRate());

            // then — (120-100)/100*100 = 20.0%
            assertThat(response.clicks().changeRate()).isEqualTo(20.0);
            assertThat(response.clicks().base()).isEqualTo(120);
            assertThat(response.clicks().previous()).isEqualTo(100);
        }

        @Test
        @DisplayName("비교 대상 날짜 데이터가 없으면 0으로 취급해 계산한다")
        void missingPreviousData_treatedAsZero() {
            Long linkId = 1L;
            LocalDate yesterday = LocalDate.of(2026, 8, 6);
            LocalDate dayBeforeYesterday = LocalDate.of(2026, 8, 5);

            when(linkDailyStatRepository.findByLinkIdAndStatDate(linkId, yesterday))
                    .thenReturn(Optional.of(stat(linkId, yesterday, 50, 40)));
            when(linkDailyStatRepository.findByLinkIdAndStatDate(linkId, dayBeforeYesterday))
                    .thenReturn(Optional.empty());

            DailyChangeResponse response = statsService.getDailyChange(linkId, yesterday);

            // 그제 데이터가 없어(0건) 클릭이 0에서 50으로 늘었으니 100%로 처리
            assertThat(response.clicks().changeRate()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("양쪽 다 0이면 증감률도 0이다 (0으로 나누기 방지)")
        void bothZero_rateIsZero() {
            Long linkId = 1L;
            LocalDate yesterday = LocalDate.of(2026, 8, 6);
            LocalDate dayBeforeYesterday = LocalDate.of(2026, 8, 5);

            when(linkDailyStatRepository.findByLinkIdAndStatDate(linkId, yesterday))
                    .thenReturn(Optional.of(stat(linkId, yesterday, 0, 0)));
            when(linkDailyStatRepository.findByLinkIdAndStatDate(linkId, dayBeforeYesterday))
                    .thenReturn(Optional.of(stat(linkId, dayBeforeYesterday, 0, 0)));

            DailyChangeResponse response = statsService.getDailyChange(linkId, yesterday);

            assertThat(response.clicks().changeRate()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("분포 조회")
    class Breakdown {

        @Test
        @DisplayName("같은 값이 여러 날짜에 걸쳐 있으면 합산해서 비율을 계산한다")
        void aggregatesAcrossDatesAndCalculatesPercentage() {
            Long linkId = 1L;
            LocalDate from = LocalDate.of(2026, 8, 1);
            LocalDate to = LocalDate.of(2026, 8, 7);

            when(dimensionStatRepository.findByLinkIdAndTypeAndDateRange(
                    linkId, LinkDailyDimensionStat.DimensionType.DEVICE, from, to))
                    .thenReturn(List.of(
                            dimensionStat(linkId, LocalDate.of(2026, 8, 6), "MOBILE", 70),
                            dimensionStat(linkId, LocalDate.of(2026, 8, 7), "MOBILE", 30),
                            dimensionStat(linkId, LocalDate.of(2026, 8, 6), "DESKTOP", 100)
                    ));

            DimensionBreakdownResponse response = statsService.getBreakdown(
                    linkId, LinkDailyDimensionStat.DimensionType.DEVICE, from, to);


            log.info("===== 분포 조회 테스트 =====");

            response.breakdown().forEach(item ->
                    log.info(
                            "value={}, clickCount={}, percentage={}%",
                            item.value(),
                            item.clickCount(),
                            item.percentage()
                    )
            );

            // MOBILE = 70+30 = 100건, DESKTOP = 100건 → 총 200건, 각각 50%
            assertThat(response.breakdown()).hasSize(2);
            assertThat(response.breakdown())
                    .extracting(DimensionBreakdownResponse.Item::value, DimensionBreakdownResponse.Item::clickCount)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.api.Assertions.tuple("MOBILE", 100),
                            org.assertj.core.api.Assertions.tuple("DESKTOP", 100)
                    );
            assertThat(response.breakdown().get(0).percentage()).isEqualTo(50.0);
        }

        @Test
        @DisplayName("데이터가 없으면 빈 목록을 반환한다")
        void noData_returnsEmptyList() {
            Long linkId = 1L;
            LocalDate from = LocalDate.of(2026, 8, 1);
            LocalDate to = LocalDate.of(2026, 8, 7);

            when(dimensionStatRepository.findByLinkIdAndTypeAndDateRange(any(), any(), any(), any()))
                    .thenReturn(List.of());

            DimensionBreakdownResponse response = statsService.getBreakdown(
                    linkId, LinkDailyDimensionStat.DimensionType.REGION, from, to);

            assertThat(response.breakdown()).isEmpty();
        }
    }

    private LinkDailyStat stat(Long linkId, LocalDate date, int clickCount, int visitorCount) {
        return LinkDailyStat.builder()
                .linkId(linkId)
                .statDate(date)
                .clickCount(clickCount)
                .visitorCount(visitorCount)
                .build();
    }

    private LinkDailyDimensionStat dimensionStat(Long linkId, LocalDate date, String value, int clickCount) {
        return LinkDailyDimensionStat.builder()
                .linkId(linkId)
                .statDate(date)
                .dimensionType(LinkDailyDimensionStat.DimensionType.DEVICE)
                .dimensionValue(value)
                .clickCount(clickCount)
                .build();
    }
}