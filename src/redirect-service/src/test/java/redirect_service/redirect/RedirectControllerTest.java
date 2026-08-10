package redirect_service.redirect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        RedirectController controller = new RedirectController(redirectService);
        when(redirectService.findRedirectUrl("abc123")).thenReturn("https://example.com/page");

        var response = controller.redirect("abc123");

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).isEqualTo(URI.create("https://example.com/page"));
        verify(redirectService).findRedirectUrl("abc123");
    }
}
