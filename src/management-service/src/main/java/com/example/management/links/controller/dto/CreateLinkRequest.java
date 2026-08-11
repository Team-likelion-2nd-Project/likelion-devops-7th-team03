package com.example.management.links.controller.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.net.URI;
import java.time.OffsetDateTime;

/** POST /api/v1/links 요청 계약. title은 links.title에 대응하는 표시용 이름이다. */
public record CreateLinkRequest(
        @NotBlank String originalUrl,
        @NotNull OffsetDateTime expiresAt,
        @Size(max = 100) String title
) {

    public static final int MAX_ORIGINAL_URL_LENGTH = 2048;

    @AssertTrue
    public boolean hasValidOriginalUrl() {
        if (originalUrl == null || originalUrl.isBlank() || originalUrl.length() > MAX_ORIGINAL_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(originalUrl);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
