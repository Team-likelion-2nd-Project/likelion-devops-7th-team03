package com.example.management.links.service;

import com.example.management.links.config.ShortUrlProperties;
import com.example.management.links.controller.dto.CreateLinkRequest;
import com.example.management.links.controller.dto.LinkResponse;
import com.example.management.links.controller.dto.LinkStatus;
import com.example.management.links.domain.Link;
import com.example.management.links.exception.InvalidExpirationException;
import com.example.management.links.exception.LinkLimitExceededException;
import com.example.management.links.repository.LinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.security.SecureRandom;

/** 링크 생성 유스케이스의 첫 최소 구현. 목록·수정·삭제는 각각의 테스트부터 추가한다. */
@Service
public class LinkService {

    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int SLUG_LENGTH = 7;
    private static final int MAX_SLUG_GENERATION_ATTEMPTS = 10;
    /** 운영 정책이 확정되기 전까지 서비스 상수로 관리한다. */
    private static final long MAX_VISIBLE_LINKS_PER_USER = 100;
    /** 운영 정책이 확정되기 전까지 서비스 상수로 관리한다. */
    private static final int MAX_EXPIRATION_YEARS = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LinkRepository linkRepository;
    private final ShortUrlProperties shortUrlProperties;

    public LinkService(LinkRepository linkRepository,
                       ShortUrlProperties shortUrlProperties) {
        this.linkRepository = linkRepository;
        this.shortUrlProperties = shortUrlProperties;
    }

    @Transactional
    public LinkResponse create(Long userId, CreateLinkRequest request) {
        validateExpiration(request.expiresAt());

        if (linkRepository.countByUserIdAndIsVisibleTrue(userId) >= MAX_VISIBLE_LINKS_PER_USER) {
            throw new LinkLimitExceededException();
        }

        Link link = Link.builder()
                .userId(userId)
                .slug(generateAvailableSlug())
                .originalUrl(request.originalUrl())
                .title(request.title())
                .expiresAt(toUtcLocalDateTime(request.expiresAt()))
                .build();

        // 설정 오류로 저장만 되고 응답 생성이 실패하는 일을 막기 위해 영속화 전에 확인한다.
        String shortUrl = shortUrl(link.getSlug());
        return toResponse(linkRepository.save(link), shortUrl);
    }

    private void validateExpiration(OffsetDateTime expiresAt) {
        if (expiresAt == null || !ZoneOffset.UTC.equals(expiresAt.getOffset())) {
            throw new InvalidExpirationException();
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!expiresAt.isAfter(now) || expiresAt.isAfter(now.plusYears(MAX_EXPIRATION_YEARS))) {
            throw new InvalidExpirationException();
        }
    }

    private String generateAvailableSlug() {
        for (int attempt = 0; attempt < MAX_SLUG_GENERATION_ATTEMPTS; attempt++) {
            String slug = generateBase62Slug();
            if (!linkRepository.existsBySlug(slug)) {
                return slug;
            }
        }
        throw new IllegalStateException("단축 URL 생성에 실패했습니다. 다시 시도해주세요.");
    }

    private String generateBase62Slug() {
        StringBuilder slug = new StringBuilder(SLUG_LENGTH);
        for (int i = 0; i < SLUG_LENGTH; i++) {
            slug.append(BASE62.charAt(SECURE_RANDOM.nextInt(BASE62.length())));
        }
        return slug.toString();
    }

    private LinkResponse toResponse(Link link, String shortUrl) {
        return new LinkResponse(
                link.getLinkId(),
                link.getTitle(),
                shortUrl,
                link.getOriginalUrl(),
                toUtcOffsetDateTime(link.getCreatedAt()),
                toUtcOffsetDateTime(link.getExpiresAt()),
                link.isExpired(LocalDateTime.now(ZoneOffset.UTC)) ? LinkStatus.EXPIRED : LinkStatus.ACTIVE
        );
    }

    private String shortUrl(String slug) {
        String baseUrl = shortUrlProperties.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("SHORT_URL_BASE_URL 설정이 필요합니다.");
        }
        return baseUrl.replaceAll("/+$", "") + "/" + slug;
    }

    private LocalDateTime toUtcLocalDateTime(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private OffsetDateTime toUtcOffsetDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atOffset(ZoneOffset.UTC);
    }
}
