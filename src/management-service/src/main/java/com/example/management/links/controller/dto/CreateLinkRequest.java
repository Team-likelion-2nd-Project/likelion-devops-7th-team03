package com.example.management.links.controller.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** POST /api/v1/links 요청 계약. title은 links.title에 대응하는 표시용 이름이다. */
public record CreateLinkRequest(
        @NotBlank String originalUrl,
        @NotNull OffsetDateTime expiresAt,
        @Size(max = 100) String title
) {

    @AssertTrue
    public boolean hasValidOriginalUrl() {
        return LinkUrlValidator.isValid(originalUrl);
    }
}
