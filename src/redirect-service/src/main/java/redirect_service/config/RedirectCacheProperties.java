package redirect_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.redirect-cache")
public class RedirectCacheProperties {

    private Duration ttl = Duration.ofMinutes(10);
}
