package com.example.management.statics.controller;

import com.example.management.stats.domain.LinkDailyDimensionStat;
import com.example.management.stats.domain.LinkDailyStat;
import com.example.management.stats.repository.LinkDailyDimensionStatRepository;
import com.example.management.stats.repository.LinkDailyStatRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

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
 *   - docker compose up -d mysql redis 로 로컬 DB/Redis가 떠있어야 함
 *   - run-migration.sh로 마이그레이션이 적용되어 있어야 함
 *   - application-test.yml(또는 application.yml)의 DB 접속 정보가 정상이어야 함
 *
 * TEMP_USER_ID(=1L)는 이 테스트가 직접 심는 유저(user_id="test-user-uuid-1")의 내부 id다.
 * CurrentUserArgumentResolver가 SecurityContext의 principal(JWT subject UUID)로
 * UserRepository를 조회해 이 내부 id를 얻으므로, setUp()에서 실제 JwtAuthenticationFilter와
 * 동일한 방식으로 SecurityContextHolder에 그 UUID를 심어준다 (@WithMockUser는 principal이
 * 문자열이 아니라서 CurrentUserArgumentResolver의 instanceof String 체크와 안 맞아 못 씀).
 * 테스트용 링크는 이 유저(id=1) 소유로 직접 INSERT해서 만들고, 끝나면 지운다
 * (기존 links 테이블 데이터에 의존하지 않아 어떤 로컬 DB 상태에서도 재현 가능하게 함).
 */
@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatsControllerIntegrationTest {

    private static final Long TEMP_USER_ID = 1L;
    private static final String TEST_USER_UUID = "test-user-uuid-" + TEMP_USER_ID;
    private static final LocalDate YESTERDAY = LocalDate.now().minusDays(1);
    private static final LocalDate DAY_BEFORE_YESTERDAY = LocalDate.now().minusDays(2);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LinkDailyStatRepository dailyStatRepository;

    @Autowired
    private LinkDailyDimensionStatRepository dimensionStatRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 이번 테스트에서 만든 링크의 외부 UUID(API 호출용) / 내부 id(DB 조회용) */
    private String testLinkUuid;
    private Long testInternalLinkId;

    @BeforeEach
    void setUp() {
        // 0) JwtAuthenticationFilter와 동일한 방식으로 SecurityContext에 로그인 유저를 심는다.
        //    CurrentUserArgumentResolver가 principal(String)로 UserRepository를 조회한다.
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(TEST_USER_UUID, null, java.util.List.of()));

        // 1) user_id=1(TEMP_USER_ID) 소유의 테스트 전용 링크를 직접 생성
        //    resolveOwnedLink()가 user_id=1 && is_visible=true 조건으로 조회하므로
        //    이 조건을 만족하는 링크가 실제로 있어야 API가 정상 동작한다.
        testLinkUuid = UUID.randomUUID().toString();
        String slug = "test-" + UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update("""
                INSERT INTO users (id, user_id, kakao_id, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', NOW(), NOW())
                ON DUPLICATE KEY UPDATE id = id
                """,
                TEMP_USER_ID, TEST_USER_UUID, 999999L, "테스트유저");

        jdbcTemplate.update("""
                INSERT INTO links (link_id, user_id, slug, original_url, is_visible, created_at, updated_at)
                VALUES (?, ?, ?, 'https://example.com', TRUE, NOW(), NOW())
                """,
                testLinkUuid, TEMP_USER_ID, slug);

        testInternalLinkId = jdbcTemplate.queryForObject(
                "SELECT id FROM links WHERE link_id = ?", Long.class, testLinkUuid);

        log.info("===== 테스트 링크 생성 =====");
        log.info("linkUuid={}, internalId={}", testLinkUuid, testInternalLinkId);

        // 2) 일별 통계 테스트 데이터
        dailyStatRepository.save(LinkDailyStat.builder()
                .linkId(testInternalLinkId)
                .statDate(YESTERDAY)
                .clickCount(120)
                .visitorCount(95)
                .build());
        dailyStatRepository.save(LinkDailyStat.builder()
                .linkId(testInternalLinkId)
                .statDate(DAY_BEFORE_YESTERDAY)
                .clickCount(100)
                .visitorCount(80)
                .build());

        // 3) 분포 테스트 데이터
        dimensionStatRepository.save(LinkDailyDimensionStat.builder()
                .linkId(testInternalLinkId)
                .statDate(YESTERDAY)
                .dimensionType(LinkDailyDimensionStat.DimensionType.DEVICE)
                .dimensionValue("MOBILE")
                .clickCount(70)
                .build());
        dimensionStatRepository.save(LinkDailyDimensionStat.builder()
                .linkId(testInternalLinkId)
                .statDate(YESTERDAY)
                .dimensionType(LinkDailyDimensionStat.DimensionType.DEVICE)
                .dimensionValue("DESKTOP")
                .clickCount(30)
                .build());

        // 4) 실시간 카운터 테스트 데이터
        //    주의: Redis 키는 "외부 UUID"가 아니라 "내부 id" 기준이다 (StatsService 구현 참조)
        redisTemplate.opsForValue().set(
                "click_count:" + testInternalLinkId + ":" + LocalDate.now(), "17");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM link_daily_dimension_stats WHERE link_id = ?", testInternalLinkId);
        jdbcTemplate.update("DELETE FROM link_daily_stats WHERE link_id = ?", testInternalLinkId);
        jdbcTemplate.update("DELETE FROM links WHERE id = ?", testInternalLinkId);
        redisTemplate.delete("click_count:" + testInternalLinkId + ":" + LocalDate.now());
        SecurityContextHolder.clearContext();
        log.info("===== 테스트 데이터 정리 완료 (linkUuid={}) =====", testLinkUuid);
    }

    @Test
    void 증감률_API가_실제_DB값_기준으로_정상_응답한다() throws Exception {
        var result = mockMvc.perform(get("/api/links/{linkId}/stats/daily-change", testLinkUuid)
                        .param("baseDate", YESTERDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkId").value(testLinkUuid))
                .andExpect(jsonPath("$.clicks.base").value(120))
                .andExpect(jsonPath("$.clicks.previous").value(100))
                .andExpect(jsonPath("$.clicks.changeRate").value(20.0))
                .andReturn();

        log.info("===== 증감률 API 테스트 성공 =====");
        log.info("응답 본문: {}", result.getResponse().getContentAsString());
    }

    @Test
    void 분포_API가_실제_DB값_기준으로_정상_응답한다() throws Exception {
        var result = mockMvc.perform(get("/api/links/{linkId}/stats/breakdown", testLinkUuid)
                        .param("type", "DEVICE")
                        .param("from", YESTERDAY.toString())
                        .param("to", YESTERDAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dimensionType").value("DEVICE"))
                .andExpect(jsonPath("$.breakdown.length()").value(2))
                .andExpect(jsonPath("$.breakdown[?(@.value=='MOBILE')].clickCount").value(70))
                .andReturn();

        log.info("===== 분포 API 테스트 성공 =====");
        log.info("응답 본문: {}", result.getResponse().getContentAsString());
    }

    @Test
    void 실시간_접속자수_API가_Redis값을_그대로_반환한다() throws Exception {
        var result = mockMvc.perform(get("/api/links/{linkId}/stats/realtime", testLinkUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkId").value(testLinkUuid))
                .andExpect(jsonPath("$.realtimeClickCount").value(17))
                .andReturn();

        log.info("===== 실시간 접속자 수 API 테스트 성공 =====");
        log.info("응답 본문: {}", result.getResponse().getContentAsString());
    }

    /**
     * 존재하지 않거나 본인 소유가 아닌 링크는 이제 "0으로 채워서 200"이 아니라
     * resolveOwnedLink()의 소유권 검증에 걸려 400을 반환한다
     * (GlobalExceptionHandler가 IllegalArgumentException -> 400으로 변환).
     */
    @Test
    void 존재하지_않는_링크는_400을_반환한다() throws Exception {
        String unknownUuid = UUID.randomUUID().toString();

        var dailyChangeResult = mockMvc.perform(get("/api/links/{linkId}/stats/daily-change", unknownUuid)
                        .param("baseDate", YESTERDAY.toString()))
                .andExpect(status().isBadRequest())
                .andReturn();

        var realtimeResult = mockMvc.perform(get("/api/links/{linkId}/stats/realtime", unknownUuid))
                .andExpect(status().isBadRequest())
                .andReturn();

        log.info("===== 존재하지 않는 링크 400 응답 테스트 성공 =====");
        log.info("daily-change 응답: {}", dailyChangeResult.getResponse().getContentAsString());
        log.info("realtime 응답: {}", realtimeResult.getResponse().getContentAsString());
    }
}