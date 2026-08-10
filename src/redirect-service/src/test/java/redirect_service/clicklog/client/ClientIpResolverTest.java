package redirect_service.clicklog.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import redirect_service.config.ClickLogProperties;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    @DisplayName("신뢰 프록시가 아니면 원격 IP를 사용한다")
    void returnsRemoteAddressWhenForwardedForIsNotTrusted() {
        ClickLogProperties properties = new ClickLogProperties();
        properties.setTrustForwardedFor(false);
        MockHttpServletRequest request = request("203.0.113.10", "198.51.100.1");

        String clientIp = new ClientIpResolver(properties).resolve(request);

        assertThat(clientIp).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("신뢰 프록시 환경에서는 X-Forwarded-For의 첫 IP를 사용한다")
    void returnsFirstForwardedForAddressWhenTrusted() {
        ClickLogProperties properties = new ClickLogProperties();
        properties.setTrustForwardedFor(true);
        MockHttpServletRequest request = request("203.0.113.10", "198.51.100.1, 192.0.2.1");

        String clientIp = new ClientIpResolver(properties).resolve(request);

        assertThat(clientIp).isEqualTo("198.51.100.1");
    }

    private MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
