package redirect_service.clicklog.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import redirect_service.clicklog.resolver.ClientIpResolver;
import redirect_service.clicklog.event.ClickEvent;
import redirect_service.clicklog.event.ClickRequestSnapshot;
import redirect_service.clicklog.event.RedirectSucceededEvent;
import redirect_service.clicklog.resolver.ReferrerCategoryResolver;
import redirect_service.clicklog.resolver.UserAgentInfo;
import redirect_service.clicklog.resolver.UserAgentResolver;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClickEventAssemblerTest {

    @Mock private ClientIpResolver clientIpResolver;
    @Mock private UserAgentResolver userAgentResolver;
    @Mock private ReferrerCategoryResolver referrerCategoryResolver;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void convertsRedirectEventToCompletedClickEvent() {
        ClickEventAssembler handler = new ClickEventAssembler(
                clientIpResolver, userAgentResolver, referrerCategoryResolver, eventPublisher);
        ClickRequestSnapshot request = new ClickRequestSnapshot("visitor-id", LocalDateTime.parse("2026-08-12T15:00:00"),
                "203.0.113.10", null, "Mozilla/5.0", "https://google.com/search", "ko-KR,ko;q=0.9");
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");
        when(userAgentResolver.resolve("Mozilla/5.0")).thenReturn(new UserAgentInfo("DESKTOP", "Mac OS", "Chrome"));
        when(referrerCategoryResolver.resolve("https://google.com/search")).thenReturn("GOOGLE");

        handler.onRedirectSucceeded(new RedirectSucceededEvent(1L, request));

        ArgumentCaptor<ClickEvent> captor = ArgumentCaptor.forClass(ClickEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue())
                .extracting(ClickEvent::linkId, ClickEvent::visitorId, ClickEvent::clientIp,
                        ClickEvent::language, ClickEvent::operatingSystem, ClickEvent::referrerCategory)
                .containsExactly(1L, "visitor-id", "203.0.113.10", "ko-KR", "Mac OS", "GOOGLE");
    }
}
