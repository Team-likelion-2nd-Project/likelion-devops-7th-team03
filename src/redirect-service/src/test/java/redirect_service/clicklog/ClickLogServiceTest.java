package redirect_service.clicklog;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redirect_service.clicklog.client.ClientIpResolver;
import redirect_service.clicklog.publisher.ClickEventPublisher;
import redirect_service.clicklog.referrer.ReferrerCategoryResolver;
import redirect_service.clicklog.useragent.UserAgentInfo;
import redirect_service.clicklog.useragent.UserAgentResolver;
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
    private ClientIpResolver clientIpResolver;

    @Mock
    private UserAgentResolver userAgentResolver;

    @Mock
    private ReferrerCategoryResolver referrerCategoryResolver;

    @Mock
    private ClickEventPublisher clickEventPublisher;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private ClickLogService clickLogService;

    @Test
    @DisplayName("클릭 정보를 수집해 이벤트로 발행하고 방문자 정보를 반환한다")
    void capturesAndPublishesClickEvent() {
        ResolvedVisitor visitor = new ResolvedVisitor("visitor-id", "visitor_id=visitor-id");
        when(visitorIdResolver.resolve(request)).thenReturn(visitor);
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(request.getHeader("Referer")).thenReturn("https://google.com/search");
        when(request.getHeader("Accept-Language")).thenReturn("ko-KR,ko;q=0.9");
        when(userAgentResolver.resolve("Mozilla/5.0"))
                .thenReturn(new UserAgentInfo("DESKTOP", "Mac OS", "Chrome"));
        when(referrerCategoryResolver.resolve("https://google.com/search")).thenReturn("GOOGLE");
        ResolvedVisitor resolvedVisitor = clickLogService.capture(1L, request);

        ArgumentCaptor<ClickEventLog> eventCaptor = ArgumentCaptor.forClass(ClickEventLog.class);
        verify(clickEventPublisher).publish(eventCaptor.capture());
        ClickEventLog clickEvent = eventCaptor.getValue();

        assertThat(resolvedVisitor).isSameAs(visitor);
        assertThat(clickEvent.linkId()).isEqualTo(1L);
        assertThat(clickEvent.visitorId()).isEqualTo("visitor-id");
        assertThat(clickEvent.clientIp()).isEqualTo("203.0.113.10");
        assertThat(clickEvent.language()).isEqualTo("ko-KR");
        assertThat(clickEvent.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(clickEvent.operatingSystem()).isEqualTo("Mac OS");
        assertThat(clickEvent.browser()).isEqualTo("Chrome");
    }
}
