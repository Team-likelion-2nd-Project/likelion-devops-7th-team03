package redirect_service.clicklog.client;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import redirect_service.config.ClickLogProperties;

@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final ClickLogProperties properties;

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
