package com.example.management.stats.controller;

import com.example.management.stats.domain.LinkDailyDimensionStat;
import com.example.management.stats.domain.LinkDailyStat;
import com.example.management.stats.repository.LinkDailyDimensionStatRepository;
import com.example.management.stats.repository.LinkDailyStatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통계 API 통합 테스트.
 *
 * 단위 테스트(StatsServiceTest)와 다르게 Mock을 쓰지 않고
 * 실제 로컬 MySQL/Redis(docker-compose로 띄운 것)에 붙어서
 * "HTTP 요청 -> Controller -> Service -> Repository -> DB" 전체 흐름을 검증한다.
 *
 * 실행 전제:
 * - docker compose up -d mysql redis 로 로컬 DB/Redis가 떠있어야 함
 * - run-migration.sh로 마이그레이션이 적용되어 있어야 함
 * - links 등의 테이블이 존재해야 함
 * - application.yml 또는 application-test.yml의 DB 접속 정보가 정상이어야 함
 *
 * 테스트 데이터는 각 테스트 실행 전(@BeforeEach)에 직접 INSERT하고,
 * 실행 후(@AfterEach)에 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "testuser", roles = {"USER"})
class StatsControllerIntegrationTest {

    /**
     * 임의의 Link ID를 사용하지 않는다.
     *
     * link_daily_stats.link_id는 links.id를 참조하는 FK이므로
     * 실제 DB에 존재하는 Link의 ID를 테스트 대상으로 사용한다.
     */
    private Long testLinkId;

    private static final LocalDate YESTERDAY =
            LocalDate.now().minusDays(1);

    private static final LocalDate DAY_BEFORE_YESTERDAY =
            LocalDate.now().minusDays(2);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LinkDailyStatRepository linkDailyStatRepository;

    @Autowired
    private LinkDailyDimensionStatRepository dimensionStatRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {

        /*
         * 실제 links 테이블에 존재하는 Link 하나를 가져온다.
         *
         * 기존:
         *   TEST_LINK_ID = 999_999L
         *
         * 문제:
         *   999999라는 Link가 실제 DB에 없기 때문에
         *   link_daily_stats INSERT 시 FK 제약조건 위반 발생.
         */
        testLinkId = jdbcTemplate.queryForObject(
                "SELECT id FROM links ORDER BY id LIMIT 1",
                Long.class
        );

        if (testLinkId == null) {
            throw new IllegalStateException(
                    "테스트를 실행할 Link가 없습니다. " +
                            "links 테이블에 최소 1개의 데이터가 필요합니다."
            );
        }

        /*
         * 테스트를 여러 번 실행해도 기존 테스트 데이터와 충돌하지 않도록
         * 먼저 테스트 대상 날짜의 데이터를 삭제한다.
         */

        List<LinkDailyStat> existingStats =
                linkDailyStatRepository.findByLinkIdAndDateRange(
                        testLinkId,
                        DAY_BEFORE_YESTERDAY,
                        YESTERDAY
                );

        linkDailyStatRepository.deleteAll(existingStats);

        List<LinkDailyDimensionStat> existingDimensionStats =
                dimensionStatRepository.findByLinkIdAndTypeAndDateRange(
                        testLinkId,
                        LinkDailyDimensionStat.DimensionType.DEVICE,
                        YESTERDAY,
                        YESTERDAY
                );

        dimensionStatRepository.deleteAll(existingDimensionStats);

        /*
         * Redis 기존 테스트 데이터 삭제
         */
        redisTemplate.delete(
                "click_count:" + testLinkId + ":" + LocalDate.now()
        );

        // =========================================================
        // 일별 통계 테스트 데이터
        // =========================================================

        linkDailyStatRepository.save(
                LinkDailyStat.builder()
                        .linkId(testLinkId)
                        .statDate(YESTERDAY)
                        .clickCount(120)
                        .visitorCount(95)
                        .build()
        );

        linkDailyStatRepository.save(
                LinkDailyStat.builder()
                        .linkId(testLinkId)
                        .statDate(DAY_BEFORE_YESTERDAY)
                        .clickCount(100)
                        .visitorCount(80)
                        .build()
        );

        // =========================================================
        // 분포 테스트 데이터
        // =========================================================

        dimensionStatRepository.save(
                LinkDailyDimensionStat.builder()
                        .linkId(testLinkId)
                        .statDate(YESTERDAY)
                        .dimensionType(
                                LinkDailyDimensionStat.DimensionType.DEVICE
                        )
                        .dimensionValue("MOBILE")
                        .clickCount(70)
                        .build()
        );

        dimensionStatRepository.save(
                LinkDailyDimensionStat.builder()
                        .linkId(testLinkId)
                        .statDate(YESTERDAY)
                        .dimensionType(
                                LinkDailyDimensionStat.DimensionType.DEVICE
                        )
                        .dimensionValue("DESKTOP")
                        .clickCount(30)
                        .build()
        );

        // =========================================================
        // 실시간 카운터 테스트 데이터
        // =========================================================

        redisTemplate.opsForValue().set(
                "click_count:" + testLinkId + ":" + LocalDate.now(),
                "17"
        );
    }

    @AfterEach
    void tearDown() {

        /*
         * 테스트에서 생성한 일별 통계 삭제
         */
        if (testLinkId != null) {

            List<LinkDailyStat> stats =
                    linkDailyStatRepository.findByLinkIdAndDateRange(
                            testLinkId,
                            DAY_BEFORE_YESTERDAY,
                            YESTERDAY
                    );

            linkDailyStatRepository.deleteAll(stats);

            /*
             * 테스트에서 생성한 분포 통계 삭제
             */
            List<LinkDailyDimensionStat> dimensionStats =
                    dimensionStatRepository.findByLinkIdAndTypeAndDateRange(
                            testLinkId,
                            LinkDailyDimensionStat.DimensionType.DEVICE,
                            YESTERDAY,
                            YESTERDAY
                    );

            dimensionStatRepository.deleteAll(dimensionStats);

            /*
             * 테스트에서 생성한 Redis 데이터 삭제
             */
            redisTemplate.delete(
                    "click_count:" + testLinkId + ":" + LocalDate.now()
            );
        }
    }

    // =============================================================
    // /daily-change
    // =============================================================

    @Test
    void 증감률_API가_실제_DB값_기준으로_정상_응답한다()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/links/{linkId}/stats/daily-change",
                                testLinkId
                        )
                                .param(
                                        "baseDate",
                                        YESTERDAY.toString()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.linkId")
                                .value(testLinkId)
                )
                .andExpect(
                        jsonPath("$.clicks.base")
                                .value(120)
                )
                .andExpect(
                        jsonPath("$.clicks.previous")
                                .value(100)
                )
                .andExpect(
                        jsonPath("$.clicks.changeRate")
                                .value(20.0)
                );
    }

    // =============================================================
    // /breakdown
    // =============================================================

    @Test
    void 분포_API가_실제_DB값_기준으로_정상_응답한다()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/links/{linkId}/stats/breakdown",
                                testLinkId
                        )
                                .param("type", "DEVICE")
                                .param(
                                        "from",
                                        YESTERDAY.toString()
                                )
                                .param(
                                        "to",
                                        YESTERDAY.toString()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.dimensionType")
                                .value("DEVICE")
                )
                .andExpect(
                        jsonPath("$.breakdown.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath(
                                "$.breakdown[?(@.value=='MOBILE')].clickCount"
                        )
                                .value(70)
                );
    }

    // =============================================================
    // /realtime
    // =============================================================

    @Test
    void 실시간_접속자수_API가_Redis값을_그대로_반환한다()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/links/{linkId}/stats/realtime",
                                testLinkId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.linkId")
                                .value(testLinkId)
                )
                .andExpect(
                        jsonPath("$.realtimeClickCount")
                                .value(17)
                );
    }

    // =============================================================
    // 데이터가 없는 Link 테스트
    // =============================================================

    @Test
    void 데이터가_없는_링크는_0으로_채워서_응답한다()
            throws Exception {

        /*
         * 실제 links 테이블에 존재하지 않는 ID를 사용해도
         * 여기서는 DB INSERT를 하지 않기 때문에 FK 문제가 발생하지 않는다.
         */
        Long emptyLinkId = 888_888L;

        mockMvc.perform(
                        get(
                                "/api/links/{linkId}/stats/daily-change",
                                emptyLinkId
                        )
                                .param(
                                        "baseDate",
                                        YESTERDAY.toString()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.clicks.base")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.clicks.previous")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.clicks.changeRate")
                                .value(0.0)
                );

        mockMvc.perform(
                        get(
                                "/api/links/{linkId}/stats/realtime",
                                emptyLinkId
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.realtimeClickCount")
                                .value(0)
                );
    }
}