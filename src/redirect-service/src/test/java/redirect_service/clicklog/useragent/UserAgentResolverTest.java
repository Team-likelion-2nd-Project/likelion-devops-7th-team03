package redirect_service.clicklog.useragent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentResolverTest {

    private final UserAgentResolver resolver = new UserAgentResolver();

    @Test
    @DisplayName("User-Agent가 없으면 UNKNOWN을 반환한다")
    void returnsUnknownForMissingUserAgent() {
        assertThat(resolver.resolve(null)).isEqualTo(new UserAgentInfo("UNKNOWN", "UNKNOWN", "UNKNOWN"));
    }
}
