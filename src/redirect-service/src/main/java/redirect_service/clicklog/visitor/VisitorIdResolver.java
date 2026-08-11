package redirect_service.clicklog.visitor;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import redirect_service.config.ClickLogProperties;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VisitorIdResolver {

    private final ClickLogProperties properties;

    public ResolvedVisitor resolve(HttpServletRequest request) {
        String existingVisitorId = findValidVisitorId(request);
        if (existingVisitorId != null) {
            return new ResolvedVisitor(existingVisitorId, null);
        }

        String visitorId = UUID.randomUUID().toString();
        String setCookieHeader = ResponseCookie.from(properties.getVisitorCookieName(), visitorId)
                .path("/")
                .maxAge(properties.getVisitorCookieMaxAge())
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite("Lax")
                .build()
                .toString();

        return new ResolvedVisitor(visitorId, setCookieHeader);
    }

    private String findValidVisitorId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (properties.getVisitorCookieName().equals(cookie.getName()) && isUuid(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return false;
        }
    }
}
