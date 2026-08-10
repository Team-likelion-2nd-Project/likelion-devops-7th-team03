package redirect_service.redirect;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

    @Mock
    private LinkRepository linkRepository;

    private RedirectService redirectService;

    @BeforeEach
    void setUp() {
        redirectService = new RedirectService(linkRepository);
    }

    @Test
    @DisplayName("활성화되고 만료되지 않은 링크의 원본 URL을 반환한다")
    void returnsOriginalUrlForRedirectableLink() {
        Link link = link("valid", "https://example.com", true, null);
        when(linkRepository.findBySlug("valid")).thenReturn(Optional.of(link));

        String originalUrl = redirectService.findRedirectUrl("valid");

        assertThat(originalUrl).isEqualTo("https://example.com");
        verify(linkRepository).findBySlug("valid");
    }

    @Test
    @DisplayName("존재하지 않는 링크는 404를 반환한다")
    void throwsNotFoundForMissingLink() {
        when(linkRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> redirectService.findRedirectUrl("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("비활성화된 링크는 404를 반환한다")
    void throwsNotFoundForInvisibleLink() {
        when(linkRepository.findBySlug("hidden"))
                .thenReturn(Optional.of(link("hidden", "https://example.com", false, null)));

        assertThatThrownBy(() -> redirectService.findRedirectUrl("hidden"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("만료된 링크는 404를 반환한다")
    void throwsNotFoundForExpiredLink() {
        when(linkRepository.findBySlug("expired"))
                .thenReturn(Optional.of(link("expired", "https://example.com", true, LocalDateTime.now().minusSeconds(1))));

        assertThatThrownBy(() -> redirectService.findRedirectUrl("expired"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Link link(String slug, String originalUrl, boolean isVisible, LocalDateTime expiresAt) {
        Link link = new Link();
        ReflectionTestUtils.setField(link, "slug", slug);
        ReflectionTestUtils.setField(link, "originalUrl", originalUrl);
        ReflectionTestUtils.setField(link, "isVisible", isVisible);
        ReflectionTestUtils.setField(link, "expiresAt", expiresAt);
        return link;
    }
}
