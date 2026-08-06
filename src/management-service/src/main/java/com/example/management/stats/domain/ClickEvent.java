package com.example.management.stats.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * click_events 테이블 (V1__init_schema.sql + V2__drop_click_events_fk.sql +
 * V3__partition_click_events.sql + V4__click_events_cookie_visitor_id.sql 기준)
 *
 * PK와 @Id가 다른 이유 (V3 파티셔닝):
 *   V3에서 실제 DB의 PRIMARY KEY는 (id, clicked_at) 복합키로 바뀌었다
 *   (MySQL 규칙상 파티션 기준 컬럼이 모든 UNIQUE 키에 포함되어야 하므로).
 *   하지만 이 엔티티는 의도적으로 @Id를 id 단일 컬럼으로만 유지한다:
 *     - id는 AUTO_INCREMENT라 그 자체로 이미 유일하다. clicked_at을 추가해도
 *       "이 값으로 행을 하나로 특정한다"는 실질적 유일성 판단 기준은 바뀌지 않는다.
 *     - findById/deleteById 등 id 기준 조회는 그대로 동작한다(id가 여전히
 *       PK의 왼쪽 컬럼이라 인덱스를 탄다 — V3 주석 참조).
 *     - 복합키를 그대로 반영하려면 @IdClass/@EmbeddedId 도입이 필요한데,
 *       click_events는 단건 조회/수정이 없는 append-only 로그 테이블이라
 *       그 비용을 들일 실익이 없다고 판단했다.
 *   ddl-auto=validate가 PK 컬럼 구성까지 엄격히 비교하는 환경이라면 검증
 *   실패 가능성이 있으니, 환경 전환 시 이 부분을 먼저 확인할 것.
 *   [미검증 — TODO] 아직 실제 스프링부트 기동으로 확인해보지 않았다. 로컬 DB에
 *   V1~V4를 전부 적용한 뒤 ddl-auto=validate로 애플리케이션을 띄워 SchemaManagementException이
 *   나는지 직접 확인할 것. 실패한다면 @IdClass/@EmbeddedId 도입 또는 ddl-auto=none
 *   전환 중 하나를 선택해야 한다 (validate와 달리 none은 시작 시 스키마 비교 자체를 하지 않는다).
 *
 * - clickedAt은 UTC로 저장한다. KST 변환은 배치 집계 시점에 처리한다.
 * - RDB 보관 기간 30일. 초과분은 S3 export 후 삭제한다.
 *   (자동 삭제는 파티셔닝 완료 후 DROP PARTITION 방식으로 구현 예정 — README 참조)
 * - linkId는 link-service 소유 리소스다. V2에서 FK를 제거했으므로 DB가 정합성을
 *   보장하지 않는다. 리다이렉트 시점에 Redis 캐시로 링크 존재를 이미 확인하므로
 *   추가 비용 없이 애플리케이션이 막을 수 있다.
 * - 봇/크롤러는 삭제하지 않고 isBot 플래그로 남기고 집계 쿼리에서만 제외한다.
 *
 * 수집 시점에만 채울 수 있는 값들:
 *   visitorId        서버가 최초 방문 시 발급해 쿠키로 내려준 UUID. 요청에 쿠키가
 *                    없으면(최초 방문) 새로 발급해 응답 Set-Cookie에 싣는 동시에,
 *                    이번 클릭 로그의 값으로도 그대로 저장해야 누락이 없다
 *                    (V4 이전에는 SHA256(IP+UA) 해시 + 일별 salt 로테이션 방식이었으나,
 *                    역산 위험과 기간 UV 집계 불가 문제로 쿠키 방식으로 교체함).
 *   region           원본 IP를 마스킹하는 순간 지역 추정이 불가능해진다.
 *   deviceType       수억 행 규모에서 userAgent 사후 파싱은 현실적으로 불가능하다.
 *   referrerCategory 조회 때마다 URL을 파싱하면 인덱스를 못 탄다.
 *
 * referrer / referrerCategory 를 둘 다 두는 이유:
 *   referrer 는 Referer 헤더 원문(디버깅·재분류용).
 *   같은 인스타그램이라도 www.instagram.com, l.instagram.com 처럼 서브도메인과
 *   쿼리스트링이 매번 달라서 원문 기준으로 GROUP BY 하면 하나로 묶이지 않는다.
 *   그래서 수집 시점에 도메인을 뽑아 카테고리로 분류한 값을 따로 저장한다.
 *   분류 로직은 02-stats-queries.sql 하단 "참고용" 쿼리의 CASE 문을 그대로 옮길 것.
 */
@Entity
@Table(
        name = "click_events",
        indexes = {
                @Index(name = "idx_click_events_link_time", columnList = "link_id, clicked_at"),
                @Index(name = "idx_click_events_clicked_at", columnList = "clicked_at"),
                @Index(name = "idx_click_events_visitor_id", columnList = "link_id, visitor_id"),
                @Index(name = "idx_click_events_is_bot", columnList = "link_id, is_bot")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClickEvent {

    private static final int USER_AGENT_MAX_LENGTH = 500;
    private static final int REFERRER_MAX_LENGTH = 500;

    /** X-Forwarded-For 부재 등으로 IP를 얻지 못한 경우의 폴백값 */
    public static final String UNKNOWN_IP_MASKED = "0.0.0.xxx";

    /** referrer 분류값. Referer 헤더가 없으면 DIRECT, 매핑에 없으면 ETC */
    public static final String REFERRER_DIRECT = "DIRECT";
    public static final String REFERRER_ETC = "ETC";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 클릭된 링크의 내부 식별자 (links.id, BIGINT). V2에서 FK를 제거했으므로 정합성은 애플리케이션 책임이다.
     * 주의: 같은 이름의 필드가 Link.linkId(String, 외부 노출용 UUID, links.link_id 컬럼)로도 존재한다.
     * 둘은 이름만 같을 뿐 타입도 가리키는 컬럼도 다르다 — 요청에서 받은 UUID를 그대로 여기 넣지 말 것.
     * 리다이렉트/수집 계층에서 반드시 UUID -> 내부 id 변환(또는 Redis 캐시 조회 결과)을 거친 뒤 넘겨야 한다.
     */
    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;

    /**
     * 방문자 식별 쿠키 값(UUID, 36자). 서버가 최초 방문 시 발급해 Set-Cookie로 내려주고,
     * 이후 요청은 쿠키에 담긴 이 값을 그대로 저장한다. IP/UA 등 개인정보로부터
     * 유도된 값이 아닌 순수 난수라 역산 위험이 없다 — 이전 SHA256(IP+UA) 해시 방식과
     * 달리 salt 로테이션이 필요 없다.
     */
    @Column(name = "visitor_id", length = 36, nullable = false)
    private String visitorId;

    /** 마지막 옥텟을 마스킹한 IP. 표시/디버깅용이며 UV 판별에는 쓰지 않는다 */
    @Column(name = "ip_masked", length = 45)
    private String ipMasked;

    @Column(name = "user_agent", length = USER_AGENT_MAX_LENGTH)
    private String userAgent;

    /** Referer 헤더 원문. 직접 유입이나 인앱 브라우저에서는 NULL이 흔하다 */
    @Column(name = "referrer", length = REFERRER_MAX_LENGTH)
    private String referrer;

    /** 수집 시점에 분류한 유입경로 (INSTAGRAM / NAVER / DIRECT / ETC ...) */
    @Column(name = "referrer_category", length = 30)
    private String referrerCategory;

    /** userAgent 파싱 결과 (MOBILE / DESKTOP / TABLET). 파싱 실패 시 null */
    @Column(name = "device_type", length = 20)
    private String deviceType;

    /** 원본 IP 기반 추정 지역. 마스킹 전 시점에만 계산 가능. 매핑 실패 시 null */
    @Column(name = "region", length = 100)
    private String region;

    /** 봇/크롤러 여부. 삭제하지 않고 플래그로만 표시하고 집계에서 제외한다 */
    @Column(name = "is_bot", nullable = false)
    private boolean isBot;

    @Builder
    private ClickEvent(Long linkId, LocalDateTime clickedAt, String visitorId, String ipMasked,
                       String userAgent, String referrer, String referrerCategory,
                       String deviceType, String region, boolean isBot) {
        this.linkId = linkId;
        this.clickedAt = clickedAt;
        this.visitorId = visitorId;
        this.ipMasked = (ipMasked == null) ? UNKNOWN_IP_MASKED : ipMasked;
        // 길이를 넘으면 DataException으로 클릭 기록 자체가 실패하므로 저장 전에 자른다
        this.userAgent = truncate(userAgent, USER_AGENT_MAX_LENGTH);
        this.referrer = truncate(referrer, REFERRER_MAX_LENGTH);
        this.referrerCategory = referrerCategory;
        this.deviceType = deviceType;
        this.region = region;
        this.isBot = isBot;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
