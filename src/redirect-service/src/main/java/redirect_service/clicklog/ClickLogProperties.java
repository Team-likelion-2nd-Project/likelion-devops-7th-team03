package redirect_service.clicklog;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.click-log")
public class ClickLogProperties {

    private String visitorCookieName = "visitor_id";
    private Duration visitorCookieMaxAge = Duration.ofDays(30);
    private boolean secureCookie;
    private boolean trustForwardedFor;
    private String geoIpDatabasePath;
}
