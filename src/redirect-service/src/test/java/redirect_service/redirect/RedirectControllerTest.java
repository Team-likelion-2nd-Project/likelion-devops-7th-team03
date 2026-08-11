package redirect_service.redirect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import redirect_service.clicklog.ClickLogService;
import redirect_service.clicklog.visitor.ResolvedVisitor;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedirectControllerTest {

    @Test
    @DisplayName("서비스가 반환한 URL을 Location 헤더에 담아 302를 반환한다")
    void redirectsToUrlReturnedByService() {
        RedirectService redirectService = mock(RedirectService.class);
        ClickLogService clickLogService = mock(ClickLogService.class);
        RedirectController controller = new RedirectController(redirectService, clickLogService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(redirectService.findRedirectTarget("abc123"))
                .thenReturn(new RedirectTarget(1L, "https://example.com/page"));
        when(clickLogService.capture(1L, request))
                .thenReturn(new ResolvedVisitor("visitor-id", "visitor_id=visitor-id; Path=/"));

        var response = controller.redirect("abc123", request);

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("https://example.com/page"));
        assertThat(response.getHeaders().getFirst("Set-Cookie")).contains("visitor_id=visitor-id");
        verify(redirectService).findRedirectTarget("abc123");
        verify(clickLogService).capture(1L, request);
    }
}
