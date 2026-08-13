package redirect_service.clicklog.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import redirect_service.clicklog.event.ClickRequestSnapshot;
import redirect_service.clicklog.resolver.ClientIpResolver;
import redirect_service.config.ClickEventProperties;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    @DisplayName("신뢰 프록시가 아니면 원격 IP를 사용한다")
    void returnsRemoteAddressWhenForwardedForIsNotTrusted() {
        ClickEventProperties properties = new ClickEventProperties();
        properties.setTrustForwardedFor(false);
        ClickRequestSnapshot request = request("203.0.113.10", "198.51.100.1");

        String clientIp = new ClientIpResolver(properties).resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("신뢰 프록시 환경에서는 X-Forwarded-For의 첫 IP를 사용한다")
    void returnsFirstForwardedForAddressWhenTrusted() {
        ClickEventProperties properties = new ClickEventProperties();
        properties.setTrustForwardedFor(true);
        ClickRequestSnapshot request = request("203.0.113.10", "198.51.100.1, 192.0.2.1");

        String clientIp = new ClientIpResolver(properties).resolve(request);

        assertThat(clientIp).isEqualTo("198.51.100.1");
    }

    private ClickRequestSnapshot request(String remoteAddress, String forwardedFor) {
        return new ClickRequestSnapshot("visitor-id", LocalDateTime.now(ZoneOffset.UTC), remoteAddress, forwardedFor,
                null, null, null);
    }
}
