package redirect_service.clicklog;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redirect_service.clicklog.publisher.ClickEventPublisher;
import redirect_service.clicklog.referrer.ReferrerCategoryResolver;
import redirect_service.clicklog.region.ClientIpResolver;
import redirect_service.clicklog.region.RegionResolver;
import redirect_service.clicklog.useragent.UserAgentDeviceResolver;
import redirect_service.clicklog.visitor.ResolvedVisitor;
import redirect_service.clicklog.visitor.VisitorIdResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickLogServiceTest {

    @Mock
    private VisitorIdResolver visitorIdResolver;

    @Mock
    private UserAgentDeviceResolver userAgentDeviceResolver;

    @Mock
    private ReferrerCategoryResolver referrerCategoryResolver;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private RegionResolver regionResolver;

    @Mock
    private ClickEventPublisher clickEventPublisher;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ClickLogService clickLogService;

    @Test
    @DisplayName("수집한 클릭 이벤트를 Publisher에 전달한다")
    void publishesCapturedClickEvent() {
        ResolvedVisitor visitor = new ResolvedVisitor("visitor-id", null);
        when(visitorIdResolver.resolve(request)).thenReturn(visitor);
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("Referer")).thenReturn("https://google.com/search");
        when(userAgentDeviceResolver.resolveDeviceType("Mozilla/5.0")).thenReturn("DESKTOP");
        when(referrerCategoryResolver.resolve("https://google.com/search")).thenReturn("GOOGLE");
        when(clientIpResolver.resolve(request)).thenReturn("127.0.0.1");
        when(regionResolver.resolve("127.0.0.1")).thenReturn("KR-11");

        ResolvedVisitor resolvedVisitor = clickLogService.capture(1L, request);

        ArgumentCaptor<ClickEventLog> eventCaptor = ArgumentCaptor.forClass(ClickEventLog.class);
        verify(clickEventPublisher).publish(eventCaptor.capture());
        ClickEventLog clickEvent = eventCaptor.getValue();
        assertThat(resolvedVisitor).isSameAs(visitor);
        assertThat(clickEvent.linkId()).isEqualTo(1L);
        assertThat(clickEvent.visitorId()).isEqualTo("visitor-id");
        assertThat(clickEvent.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(clickEvent.deviceType()).isEqualTo("DESKTOP");
        assertThat(clickEvent.referrerCategory()).isEqualTo("GOOGLE");
        assertThat(clickEvent.region()).isEqualTo("KR-11");
    }
}
