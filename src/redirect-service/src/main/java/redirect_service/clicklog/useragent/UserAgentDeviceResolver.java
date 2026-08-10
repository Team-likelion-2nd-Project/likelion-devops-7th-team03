package redirect_service.clicklog.useragent;

import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class UserAgentDeviceResolver {

    private final UserAgentAnalyzer analyzer = UserAgentAnalyzer.newBuilder()
            .hideMatcherLoadStats()
            .withField("DeviceClass")
            .withCache(1_000)
            .build();

    public String resolveDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "UNKNOWN";
        }

        String deviceClass = analyzer.parse(userAgent).getValue("DeviceClass");
        if (deviceClass == null) {
            return "UNKNOWN";
        }

        return switch (deviceClass.toUpperCase(Locale.ROOT)) {
            case "PHONE", "MOBILE" -> "MOBILE";
            case "TABLET" -> "TABLET";
            case "DESKTOP" -> "DESKTOP";
            default -> "UNKNOWN";
        };
    }
}
