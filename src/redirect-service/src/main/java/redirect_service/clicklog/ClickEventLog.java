package redirect_service.clicklog;

import java.time.Instant;

/** DB/메시지 전송 전 단계에서 구조화 로그로만 남기는 클릭 이벤트다. */
public record ClickEventLog(
        Long linkId,
        Instant clickedAt,
        String visitorId,
        String userAgent,
        String deviceType,
        String referrer,
        String referrerCategory,
        String region
) {
}
