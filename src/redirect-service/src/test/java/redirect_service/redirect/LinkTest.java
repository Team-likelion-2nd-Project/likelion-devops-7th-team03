package redirect_service.redirect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LinkTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 8, 10, 12, 0);

    @Test
    @DisplayName("활성화되고 만료일이 없으면 리다이렉트할 수 있다")
    void isRedirectableWhenVisibleAndExpirationIsAbsent() {
        Link link = link(true, null);

        assertThat(link.isRedirectable(now)).isTrue();
    }

    @Test
    @DisplayName("비활성화된 링크는 만료되지 않아도 리다이렉트할 수 없다")
    void isNotRedirectableWhenInvisible() {
        Link link = link(false, now.plusDays(1));

        assertThat(link.isRedirectable(now)).isFalse();
    }

    @Test
    @DisplayName("만료일이 현재보다 이르면 리다이렉트할 수 없다")
    void isNotRedirectableWhenExpired() {
        Link link = link(true, now.minusNanos(1));

        assertThat(link.isRedirectable(now)).isFalse();
    }

    @Test
    @DisplayName("만료일이 현재와 같아도 리다이렉트할 수 없다")
    void isNotRedirectableWhenExpirationEqualsNow() {
        Link link = link(true, now);

        assertThat(link.isRedirectable(now)).isFalse();
    }

    @Test
    @DisplayName("만료일이 현재보다 나중이면 리다이렉트할 수 있다")
    void isRedirectableWhenExpirationIsInFuture() {
        Link link = link(true, now.plusNanos(1));

        assertThat(link.isRedirectable(now)).isTrue();
    }

    private Link link(boolean isVisible, LocalDateTime expiresAt) {
        Link link = new Link();
        ReflectionTestUtils.setField(link, "isVisible", isVisible);
        ReflectionTestUtils.setField(link, "expiresAt", expiresAt);
        return link;
    }
}
