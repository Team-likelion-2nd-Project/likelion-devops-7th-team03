package redirect_service.clicklog.visitor;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import redirect_service.clicklog.ClickLogProperties;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VisitorIdResolverTest {

    private final VisitorIdResolver visitorIdResolver = new VisitorIdResolver(properties());

    @Test
    @DisplayName("visitor_id 쿠키가 없으면 UUID를 발급하고 Set-Cookie 헤더를 만든다")
    void issuesVisitorIdWhenCookieIsAbsent() {
        ResolvedVisitor visitor = visitorIdResolver.resolve(new MockHttpServletRequest());

        assertThatCodeAcceptsUuid(visitor.visitorId());
        assertThat(visitor.setCookieHeader()).contains("visitor_id=" + visitor.visitorId());
        assertThat(visitor.setCookieHeader()).contains("Path=/", "HttpOnly", "SameSite=Lax");
    }

    @Test
    @DisplayName("유효한 visitor_id 쿠키가 있으면 기존 값을 재사용한다")
    void reusesExistingVisitorId() {
        String visitorId = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("visitor_id", visitorId));

        ResolvedVisitor visitor = visitorIdResolver.resolve(request);

        assertThat(visitor.visitorId()).isEqualTo(visitorId);
        assertThat(visitor.setCookieHeader()).isNull();
    }

    @Test
    @DisplayName("형식이 잘못된 visitor_id 쿠키는 새 UUID로 교체한다")
    void replacesInvalidVisitorId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("visitor_id", "not-a-uuid"));

        ResolvedVisitor visitor = visitorIdResolver.resolve(request);

        assertThatCodeAcceptsUuid(visitor.visitorId());
        assertThat(visitor.setCookieHeader()).isNotNull();
    }

    private ClickLogProperties properties() {
        ClickLogProperties properties = new ClickLogProperties();
        properties.setVisitorCookieName("visitor_id");
        return properties;
    }

    private void assertThatCodeAcceptsUuid(String value) {
        assertThat(UUID.fromString(value)).isNotNull();
    }
}
