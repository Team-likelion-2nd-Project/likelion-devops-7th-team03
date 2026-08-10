package redirect_service.clicklog;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redirect_service.clicklog.publisher.ClickEventPublisher;
import redirect_service.clicklog.referrer.ReferrerCategoryResolver;
import redirect_service.clicklog.region.ClientIpResolver;
import redirect_service.clicklog.region.RegionResolver;
import redirect_service.clicklog.useragent.UserAgentDeviceResolver;
import redirect_service.clicklog.visitor.ResolvedVisitor;
import redirect_service.clicklog.visitor.VisitorIdResolver;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ClickLogService {

    private static final int USER_AGENT_MAX_LENGTH = 500;
    private static final int REFERRER_MAX_LENGTH = 500;

    private final VisitorIdResolver visitorIdResolver;
    private final UserAgentDeviceResolver userAgentDeviceResolver;
    private final ReferrerCategoryResolver referrerCategoryResolver;
    private final ClientIpResolver clientIpResolver;
    private final RegionResolver regionResolver;
    private final ClickEventPublisher clickEventPublisher;

    public ResolvedVisitor capture(Long linkId, HttpServletRequest request) {
        ResolvedVisitor visitor = visitorIdResolver.resolve(request);
        String userAgent = truncate(request.getHeader("User-Agent"), USER_AGENT_MAX_LENGTH);
        String referrer = truncate(request.getHeader("Referer"), REFERRER_MAX_LENGTH);

        ClickEventLog clickEvent = new ClickEventLog(
                linkId,
                Instant.now(),
                visitor.visitorId(),
                userAgent,
                userAgentDeviceResolver.resolveDeviceType(userAgent),
                referrer,
                referrerCategoryResolver.resolve(referrer),
                regionResolver.resolve(clientIpResolver.resolve(request))
        );
        clickEventPublisher.publish(clickEvent);

        return visitor;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
