package com.example.management.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * [뼈대]
 * DimensionStatsUpsertRepository의 chunkSize/maxRetry/bisectFloor/circuitBreakerThreshold를
 * 작은 값으로 주입하고, Sleeper를 no-op으로 넣어서 재시도·bisect를 실제 대기 없이 검증한다.
 * (테스트 전용 생성자는 DimensionStatsUpsertRepository에 package-private으로 추가돼 있음)
 *
 * REGION 타입은 bucketTopNPerLink 로직이 끼어들어서 row 개수/구성이 예측하기 어려워지므로,
 * 여기 테스트는 전부 REFERRER(그대로 저장되는 타입)로 통일해서 청크 분할 동작만 순수하게 본다.
 */
@ExtendWith(MockitoExtension.class)
class DimensionStatsUpsertRepositoryTest {

    private static final LocalDate STAT_DATE = LocalDate.of(2026, 8, 20);
    private static final DimensionStatsUpsertRepository.Sleeper NO_OP_SLEEPER = millis -> { /* 테스트에선 대기 안 함 */ };

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    private DimensionStatsUpsertRepository repository;

    @BeforeEach
    void setUp() {
        // 기본 파라미터. 개별 테스트에서 필요하면 newRepository(...)로 다시 만들어서 씀.
        repository = newRepository(/*chunkSize*/ 4, /*maxRetry*/ 2, /*bisectFloor*/ 1, /*circuitBreakerThreshold*/ 2);
    }

    private DimensionStatsUpsertRepository newRepository(int chunkSize, int maxRetry, int bisectFloor, int circuitBreakerThreshold) {
        return new DimensionStatsUpsertRepository(
                jdbcTemplate, chunkSize, maxRetry, /*baseBackoffMs*/ 1L, bisectFloor, circuitBreakerThreshold, NO_OP_SLEEPER);
    }

    @Test
    void 모든_row가_한번에_성공하면_재시도없이_끝난다() {
        List<AthenaBatchRunner.DimensionStatRow> rows = rows(1, 8); // chunkSize=4 → 청크 2개
        stubAlwaysSucceed();

        repository.upsertAll(rows);

        // 청크 2개, 재시도 없이 한 번씩만 batchUpdate 호출됨
        verify(jdbcTemplate, times(2)).batchUpdate(anyString(), any(SqlParameterSource[].class));
    }

    @Test
    void 일시적_실패는_재시도로_복구된다() {
        List<AthenaBatchRunner.DimensionStatRow> rows = rows(1, 4); // 단일 청크

        // 첫 호출은 실패, 두 번째 호출부터 성공 (maxRetry=2라 두 번째 시도에서 성공해야 통과)
        when(jdbcTemplate.batchUpdate(anyString(), any(SqlParameterSource[].class)))
                .thenThrow(new DataAccessResourceFailureException("일시적 커넥션 오류"))
                .thenReturn(new int[]{1, 1, 1, 1});

        repository.upsertAll(rows);

        verify(jdbcTemplate, times(2)).batchUpdate(anyString(), any(SqlParameterSource[].class));
    }

    @Test
    void 특정_row가_계속_실패하면_bisect로_격리되고_배치는_계속된다() {
        // linkId=999L 하나만 계속 실패하는 "불량 row"로 설정. 나머지는 정상.
        long poisonLinkId = 999L;
        List<AthenaBatchRunner.DimensionStatRow> rows = rows(1, 7); // 7개 (poison 포함해서 8개는 아님, chunkSize=4로 2청크)
        rows.set(3, poisonRow(poisonLinkId)); // 첫 청크(1~4) 안에 섞어 넣음

        stubFailOnlyForLinkId(poisonLinkId);

        // 예외 없이 끝나야 한다 — poison row는 dead-letter로 스킵되고 나머지는 정상 반영됨
        repository.upsertAll(rows);

        // poison row 하나만 담긴 batchUpdate 호출이 최소 maxRetry번은 있었어야 bisect가 끝까지 격리한 것
        ArgumentCaptor<SqlParameterSource[]> captor = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbcTemplate, atLeastOnce()).batchUpdate(anyString(), captor.capture());

        long poisonOnlyCalls = captor.getAllValues().stream()
                .filter(params -> params.length == 1 && linkIdOf(params[0]) == poisonLinkId)
                .count();
        assertThat(poisonOnlyCalls).isGreaterThanOrEqualTo(2); // maxRetry=2

        // 정상 row(linkId=1~3,5~7)만 담긴 성공 호출도 있어야 함 (poison이 나머지를 다 물귀신처럼 끌고 가지 않는지 확인)
        boolean anyCleanBatchSucceeded = captor.getAllValues().stream()
                .anyMatch(params -> params.length > 1
                        && java.util.Arrays.stream(params).noneMatch(p -> linkIdOf(p) == poisonLinkId));
        assertThat(anyCleanBatchSucceeded).isTrue();
    }

    @Test
    void 연속으로_청크가_실패하면_서킷브레이커가_배치를_중단시킨다() {
        // chunkSize=2, bisectFloor=1, circuitBreakerThreshold=2 → 6개 row = 청크 3개(2,2,2)
        // 전부 실패하는 상황을 만들어서 청크1,2가 연속 실패 → 청크3 처리 전에 중단돼야 함
        repository = newRepository(/*chunkSize*/ 2, /*maxRetry*/ 1, /*bisectFloor*/ 1, /*circuitBreakerThreshold*/ 2);
        List<AthenaBatchRunner.DimensionStatRow> rows = rows(1, 6);

        stubAlwaysFail();

        assertThatThrownBy(() -> repository.upsertAll(rows))
                .isInstanceOf(BatchCircuitBreakerException.class)
                .hasMessageContaining("연속 실패");

        // 청크3(linkId=5,6)에 해당하는 row는 애초에 batchUpdate까지 도달하면 안 됨
        ArgumentCaptor<SqlParameterSource[]> captor = ArgumentCaptor.forClass(SqlParameterSource[].class);
        verify(jdbcTemplate, atLeastOnce()).batchUpdate(anyString(), captor.capture());

        Set<Long> attemptedLinkIds = captor.getAllValues().stream()
                .flatMap(params -> java.util.Arrays.stream(params))
                .map(this::linkIdOf)
                .collect(Collectors.toSet());

        assertThat(attemptedLinkIds).doesNotContain(5L, 6L);
    }

    // ---- 헬퍼 ----

    private void stubAlwaysSucceed() {
        when(jdbcTemplate.batchUpdate(anyString(), any(SqlParameterSource[].class)))
                .thenAnswer(invocation -> {
                    SqlParameterSource[] params = invocation.getArgument(1);
                    int[] result = new int[params.length];
                    java.util.Arrays.fill(result, 1);
                    return result;
                });
    }

    private void stubAlwaysFail() {
        when(jdbcTemplate.batchUpdate(anyString(), any(SqlParameterSource[].class)))
                .thenThrow(new DataAccessResourceFailureException("DB 장애 시뮬레이션"));
    }

    /** poison linkId가 하나라도 섞인 batch면 실패, 아니면 성공 */
    private void stubFailOnlyForLinkId(long poisonLinkId) {
        when(jdbcTemplate.batchUpdate(anyString(), any(SqlParameterSource[].class)))
                .thenAnswer(invocation -> {
                    SqlParameterSource[] params = invocation.getArgument(1);
                    boolean containsPoison = java.util.Arrays.stream(params)
                            .anyMatch(p -> linkIdOf(p) == poisonLinkId);
                    if (containsPoison) {
                        throw new DataAccessResourceFailureException("poison row 포함된 batch 실패");
                    }
                    int[] result = new int[params.length];
                    java.util.Arrays.fill(result, 1);
                    return result;
                });
    }

    private long linkIdOf(SqlParameterSource p) {
        return (Long) p.getValue("linkId");
    }

    private List<AthenaBatchRunner.DimensionStatRow> rows(int fromInclusive, int toInclusive) {
        return LongStream.rangeClosed(fromInclusive, toInclusive)
                .mapToObj(linkId -> new AthenaBatchRunner.DimensionStatRow(
                        linkId, STAT_DATE, "REFERRER", "google.com", 10))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private AthenaBatchRunner.DimensionStatRow poisonRow(long linkId) {
        return new AthenaBatchRunner.DimensionStatRow(linkId, STAT_DATE, "REFERRER", "poison.example", 1);
    }
}