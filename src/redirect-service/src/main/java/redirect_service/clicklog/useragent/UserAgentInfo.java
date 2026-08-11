package redirect_service.clicklog.useragent;

public record UserAgentInfo(
        String deviceType,
        String operatingSystem,
        String browser
) {
}
