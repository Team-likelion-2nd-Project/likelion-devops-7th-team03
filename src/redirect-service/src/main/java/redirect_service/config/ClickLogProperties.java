package redirect_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.click-log")
public class ClickLogProperties {

    private String visitorCookieName = "visitor_id";
    private Duration visitorCookieMaxAge = Duration.ofDays(365);
    private boolean secureCookie = true;
    private boolean trustForwardedFor = true;
}
