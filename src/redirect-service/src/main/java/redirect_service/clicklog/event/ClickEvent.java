package redirect_service.clicklog.event;

import java.time.LocalDateTime;

/** 클릭 로그 소비자가 공통으로 사용하는, 해석이 끝난 이벤트다. */
public record ClickEvent(
        Long linkId,
        LocalDateTime clickedAt,
        String visitorId,
        String clientIp,
        String language,
        String userAgent,
        String deviceType,
        String operatingSystem,
        String browser,
        String referrer,
        String referrerCategory
) {
}
