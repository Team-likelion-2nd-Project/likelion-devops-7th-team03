package redirect_service.clicklog.useragent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentResolverTest {

    private final UserAgentResolver resolver = new UserAgentResolver();

    @Test
    @DisplayName("데스크톱 User-Agent에서 기기, 운영체제, 브라우저를 추출한다")
    void resolvesDesktopUserAgent() {
        UserAgentInfo userAgentInfo = resolver.resolve(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        );

        assertThat(userAgentInfo.deviceType()).isEqualTo("DESKTOP");
        assertThat(userAgentInfo.operatingSystem()).isEqualTo("Mac OS");
        assertThat(userAgentInfo.browser()).isEqualTo("Chrome");
    }

    @Test
    @DisplayName("User-Agent가 없으면 UNKNOWN을 반환한다")
    void returnsUnknownForMissingUserAgent() {
        assertThat(resolver.resolve(null)).isEqualTo(new UserAgentInfo("UNKNOWN", "UNKNOWN", "UNKNOWN"));
    }
}
