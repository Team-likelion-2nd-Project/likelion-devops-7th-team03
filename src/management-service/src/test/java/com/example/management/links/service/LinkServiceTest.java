package com.example.management.links.service;

import com.example.management.links.config.ShortUrlProperties;
import com.example.management.links.controller.dto.CreateLinkRequest;
import com.example.management.links.controller.dto.LinkResponse;
import com.example.management.links.controller.dto.LinkStatus;
import com.example.management.links.domain.Link;
import com.example.management.links.exception.InvalidExpirationException;
import com.example.management.links.exception.LinkLimitExceededException;
import com.example.management.links.repository.LinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    private LinkRepository linkRepository;

    private LinkService linkService;

    @BeforeEach
    void setUp() {
        linkService = new LinkService(linkRepository, new ShortUrlProperties("https://s.short.ly"));
    }

    @Test
    @DisplayName("create: 유효한 요청이면 현재 사용자 소유의 랜덤 Base62 링크를 저장하고 반환한다")
    void create_validRequest_savesLinkAndReturnsResponse() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        CreateLinkRequest request = new CreateLinkRequest("https://example.com/very/long/path?query=1", expiresAt, "my-blog");

        when(linkRepository.existsBySlug(anyString())).thenReturn(false);
        saveWithCreatedAt();

        LinkResponse response = linkService.create(10L, request);

        ArgumentCaptor<Link> linkCaptor = ArgumentCaptor.forClass(Link.class);
        verify(linkRepository).save(linkCaptor.capture());
        Link saved = linkCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(10L);
        assertThat(saved.getTitle()).isEqualTo("my-blog");
        assertThat(saved.getOriginalUrl()).isEqualTo(request.originalUrl());
        assertThat(saved.getSlug()).matches("[0-9A-Za-z]{7}");
        assertThat(response.linkId()).isEqualTo(saved.getLinkId());
        assertThat(response.shortUrl()).isEqualTo("https://s.short.ly/" + saved.getSlug());
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.status()).isEqualTo(LinkStatus.ACTIVE);
    }

    @Test
    @DisplayName("create: 이미 만료된 expiresAt이면 저장하지 않고 요청 오류를 던진다")
    void create_expiredAt_throwsAndDoesNotSave() {
        CreateLinkRequest request = new CreateLinkRequest(
                "https://example.com", OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1), "expired");

        assertThrows(InvalidExpirationException.class, () -> linkService.create(10L, request));

        verify(linkRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: expiresAt은 현재로부터 3년을 초과할 수 없다")
    void create_expiresAtMoreThanThreeYearsAway_throwsAndDoesNotAccessRepositories() {
        CreateLinkRequest request = new CreateLinkRequest(
                "https://example.com", OffsetDateTime.now(ZoneOffset.UTC).plusYears(3).plusSeconds(1), "too-far");

        assertThrows(InvalidExpirationException.class, () -> linkService.create(10L, request));

        verifyNoInteractions(linkRepository);
    }

    @Test
    @DisplayName("create: expiresAt이 없으면 저장하지 않고 만료일 오류를 던진다")
    void create_withoutExpiresAt_throwsInvalidExpiration() {
        assertThrows(InvalidExpirationException.class, () -> linkService.create(
                10L, new CreateLinkRequest("https://example.com", null, null)));

        verifyNoInteractions(linkRepository);
    }

    @Test
    @DisplayName("create: expiresAt이 UTC가 아니면 저장하지 않고 만료일 오류를 던진다")
    void create_nonUtcExpiresAt_throwsInvalidExpiration() {
        OffsetDateTime nonUtcExpiration = OffsetDateTime.now(ZoneOffset.ofHours(9)).plusDays(1);

        assertThrows(InvalidExpirationException.class, () -> linkService.create(
                10L, new CreateLinkRequest("https://example.com", nonUtcExpiration, null)));

        verifyNoInteractions(linkRepository);
    }

    @Test
    @DisplayName("create: visible 링크가 100개면 저장하지 않고 링크 제한 오류를 던진다")
    void create_linkLimitExceeded_throwsAndDoesNotSave() {
        when(linkRepository.countByUserIdAndIsVisibleTrue(10L)).thenReturn(100L);

        assertThrows(LinkLimitExceededException.class, () -> linkService.create(
                10L, new CreateLinkRequest("https://example.com", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), "title")));

        verify(linkRepository, never()).save(any());
        verify(linkRepository, never()).existsBySlug(anyString());
    }

    @Test
    @DisplayName("create: 이미 사용 중인 slug가 나오면 새 slug를 생성해 저장한다")
    void create_slugCollision_generatesAnotherSlug() {
        when(linkRepository.existsBySlug(anyString())).thenReturn(true, false);
        saveWithCreatedAt();

        linkService.create(10L, new CreateLinkRequest(
                "https://example.com", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), "title"));

        verify(linkRepository, org.mockito.Mockito.times(2)).existsBySlug(anyString());
        verify(linkRepository).save(any(Link.class));
    }

    @Test
    @DisplayName("create: short URL base 설정이 없으면 저장 전에 실패한다")
    void create_missingShortUrlBase_throwsBeforeSave() {
        LinkService serviceWithoutBaseUrl = new LinkService(
                linkRepository, new ShortUrlProperties(" "));
        when(linkRepository.existsBySlug(anyString())).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> serviceWithoutBaseUrl.create(
                10L, new CreateLinkRequest("https://example.com", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), "title")));

        verify(linkRepository, never()).save(any());
    }

    private void saveWithCreatedAt() {
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> {
            Link saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 8, 10, 12, 0));
            return saved;
        });
    }
}
