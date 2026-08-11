package redirect_service.redirect;

import redirect_service.common.exception.RedirectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redirect_service.redirect.RedirectCacheEntry;
import redirect_service.redirect.RedisRedirectCache;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private RedisRedirectCache redirectCache;

    private RedirectService redirectService;

    @BeforeEach
    void setUp() {
        redirectService = new RedirectService(linkRepository, redirectCache);
    }

    @Test
    @DisplayName("활성화되고 만료되지 않은 링크의 원본 URL을 반환한다")
    void returnsOriginalUrlForRedirectableLink() {
        Link link = link("valid", "https://example.com", true, null);
        when(redirectCache.get("valid")).thenReturn(Optional.empty());
        when(linkRepository.findBySlug("valid")).thenReturn(Optional.of(link));

        RedirectTarget redirectTarget = redirectService.findRedirectTarget("valid");

        assertThat(redirectTarget.linkId()).isEqualTo(1L);
        assertThat(redirectTarget.originalUrl()).isEqualTo("https://example.com");
        verify(linkRepository).findBySlug("valid");
        verify(redirectCache).put("valid", new RedirectCacheEntry(1L, "https://example.com", true, null));
    }

    @Test
    @DisplayName("캐시된 링크가 유효하면 DB를 조회하지 않는다")
    void returnsCachedRedirectTargetWithoutDatabaseLookup() {
        when(redirectCache.get("cached"))
                .thenReturn(Optional.of(new RedirectCacheEntry(1L, "https://example.com", true, null)));

        RedirectTarget redirectTarget = redirectService.findRedirectTarget("cached");

        assertThat(redirectTarget.linkId()).isEqualTo(1L);
        assertThat(redirectTarget.originalUrl()).isEqualTo("https://example.com");
        verifyNoInteractions(linkRepository);
    }

    @Test
    @DisplayName("비활성 캐시는 DB를 조회하지 않고 404를 반환한다")
    void throwsNotFoundForDisabledCachedLink() {
        when(redirectCache.get("disabled"))
                .thenReturn(Optional.of(new RedirectCacheEntry(1L, "https://example.com", false, null)));

        assertThatThrownBy(() -> redirectService.findRedirectTarget("disabled"))
                .isInstanceOf(RedirectNotFoundException.class);

        verifyNoInteractions(linkRepository);
    }

    @Test
    @DisplayName("존재하지 않는 링크는 404를 반환한다")
    void throwsNotFoundForMissingLink() {
        when(redirectCache.get("missing")).thenReturn(Optional.empty());
        when(linkRepository.findBySlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> redirectService.findRedirectTarget("missing"))
                .isInstanceOf(RedirectNotFoundException.class);
    }

    @Test
    @DisplayName("Soft 삭제된 링크는 404를 반환한다")
    void throwsNotFoundForInvisibleLink() {
        when(redirectCache.get("hidden")).thenReturn(Optional.empty());
        when(linkRepository.findBySlug("hidden"))
                .thenReturn(Optional.of(link("hidden", "https://example.com", false, null)));

        assertThatThrownBy(() -> redirectService.findRedirectTarget("hidden"))
                .isInstanceOf(RedirectNotFoundException.class);
        verify(redirectCache).put("hidden", new RedirectCacheEntry(1L, "https://example.com", false, null));
    }

    @Test
    @DisplayName("만료된 링크는 404를 반환한다")
    void throwsNotFoundForExpiredLink() {
        when(redirectCache.get("expired")).thenReturn(Optional.empty());
        when(linkRepository.findBySlug("expired"))
                .thenReturn(Optional.of(link("expired", "https://example.com", true, LocalDateTime.now().minusSeconds(1))));

        assertThatThrownBy(() -> redirectService.findRedirectTarget("expired"))
                .isInstanceOf(RedirectNotFoundException.class);
    }

    @Test
    @DisplayName("만료된 캐시는 DB를 조회하지 않고 404를 반환한다")
    void throwsNotFoundForExpiredCachedLink() {
        when(redirectCache.get("expired-cache"))
                .thenReturn(Optional.of(new RedirectCacheEntry(1L, "https://example.com", true, LocalDateTime.now().minusSeconds(1))));

        assertThatThrownBy(() -> redirectService.findRedirectTarget("expired-cache"))
                .isInstanceOf(RedirectNotFoundException.class);

        verifyNoInteractions(linkRepository);
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
