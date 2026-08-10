package redirect_service.clicklog;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redirect_service.clicklog.client.ClientIpResolver;
import redirect_service.clicklog.publisher.ClickEventPublisher;
import redirect_service.clicklog.referrer.ReferrerCategoryResolver;
import redirect_service.clicklog.useragent.UserAgentInfo;
import redirect_service.clicklog.useragent.UserAgentResolver;
import redirect_service.clicklog.visitor.ResolvedVisitor;
import redirect_service.clicklog.visitor.VisitorIdResolver;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ClickLogService {

    private static final int USER_AGENT_MAX_LENGTH = 500;
    private static final int REFERRER_MAX_LENGTH = 500;
    private static final int LANGUAGE_MAX_LENGTH = 50;

    private final VisitorIdResolver visitorIdResolver;
    private final ClientIpResolver clientIpResolver;
    private final UserAgentResolver userAgentResolver;
    private final ReferrerCategoryResolver referrerCategoryResolver;
    private final ClickEventPublisher clickEventPublisher;

    public ResolvedVisitor capture(Long linkId, HttpServletRequest request) {
        ResolvedVisitor visitor = visitorIdResolver.resolve(request);
        String userAgent = truncate(request.getHeader("User-Agent"), USER_AGENT_MAX_LENGTH);
        String referrer = truncate(request.getHeader("Referer"), REFERRER_MAX_LENGTH);
        UserAgentInfo userAgentInfo = userAgentResolver.resolve(userAgent);

        ClickEventLog clickEvent = new ClickEventLog(
                linkId,
                Instant.now(),
                visitor.visitorId(),
                clientIpResolver.resolve(request),
                resolveLanguage(request.getHeader("Accept-Language")),
                userAgent,
                userAgentInfo.deviceType(),
                userAgentInfo.operatingSystem(),
                userAgentInfo.browser(),
                referrer,
                referrerCategoryResolver.resolve(referrer)
        );
        clickEventPublisher.publish(clickEvent);

        return visitor;
    }

    private String resolveLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return null;
        }
        String primaryLanguage = acceptLanguage.split(",", 2)[0].split(";", 2)[0].trim();
        return truncate(primaryLanguage, LANGUAGE_MAX_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
