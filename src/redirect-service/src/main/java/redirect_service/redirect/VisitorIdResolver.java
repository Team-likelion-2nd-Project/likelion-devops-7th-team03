package redirect_service.redirect;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import redirect_service.config.ClickLogProperties;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class VisitorIdResolver {

    private final ClickLogProperties properties;

    public String resolve(String existingVisitorId) {
        if (isUuid(existingVisitorId)) {
            return existingVisitorId;
        }
        return UUID.randomUUID().toString();
    }

    public boolean needsCookie(String existingVisitorId) {
        return !isUuid(existingVisitorId);
    }

    public String setCookieHeader(String visitorId) {
        return ResponseCookie.from(properties.getVisitorCookieName(), visitorId)
                .path("/")
                .maxAge(properties.getVisitorCookieMaxAge())
                .httpOnly(true)
                .secure(properties.isSecureCookie())
                .sameSite("Lax")
                .build()
                .toString();
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
