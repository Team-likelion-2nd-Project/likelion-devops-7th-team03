package redirect_service.clicklog.region;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import redirect_service.clicklog.ClickLogProperties;

@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final ClickLogProperties properties;

    /** IP는 GeoIP 조회에만 쓰며 ClickEventLog에는 넣지 않는다. */
    public String resolve(HttpServletRequest request) {
        if (properties.isTrustForwardedFor()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",", 2)[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
