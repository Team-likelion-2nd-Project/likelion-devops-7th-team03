package redirect_service.redirect;

import redirect_service.common.exception.RedirectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

        RedirectTarget redirectTarget = redirectService.findRedirectTarget("valid");

        assertThat(redirectTarget.linkId()).isEqualTo(1L);
        assertThat(redirectTarget.originalUrl()).isEqualTo("https://example.com");
        verify(linkRepository).findBySlug("valid");
    }

    @Test
    @DisplayName("존재하지 않는 링크는 404를 반환한다")
    void throwsNotFoundForMissingLink() {
        when(linkRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> redirectService.findRedirectTarget("missing"))
                .isInstanceOf(RedirectNotFoundException.class);
    }

    @Test
    @DisplayName("Soft 삭제된 링크는 404를 반환한다")
    void throwsNotFoundForInvisibleLink() {
        when(linkRepository.findBySlug("hidden"))
                .thenReturn(Optional.of(link("hidden", "https://example.com", false, null)));

        assertThatThrownBy(() -> redirectService.findRedirectTarget("hidden"))
                .isInstanceOf(RedirectNotFoundException.class);
    }

    @Test
    @DisplayName("만료된 링크는 404를 반환한다")
    void throwsNotFoundForExpiredLink() {
        when(linkRepository.findBySlug("expired"))
                .thenReturn(Optional.of(link("expired", "https://example.com", true, LocalDateTime.now().minusSeconds(1))));

        assertThatThrownBy(() -> redirectService.findRedirectTarget("expired"))
                .isInstanceOf(RedirectNotFoundException.class);
    }

    private Link link(String slug, String originalUrl, boolean isVisible, LocalDateTime expiresAt) {
        Link link = new Link();
        ReflectionTestUtils.setField(link, "id", 1L);
        ReflectionTestUtils.setField(link, "slug", slug);
        ReflectionTestUtils.setField(link, "originalUrl", originalUrl);
        ReflectionTestUtils.setField(link, "isVisible", isVisible);
        ReflectionTestUtils.setField(link, "expiresAt", expiresAt);
        return link;
    }
}
