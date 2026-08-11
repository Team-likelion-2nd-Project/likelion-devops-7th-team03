package com.example.management.links.controller.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class CreateLinkRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void validRequest_hasNoViolations() {
        CreateLinkRequest request = new CreateLinkRequest(
                "https://example.com/path", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), "title");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void nonHttpUrl_hasViolation() {
        CreateLinkRequest request = new CreateLinkRequest(
                "javascript:alert(1)", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void originalUrlLongerThan2048Characters_hasViolation() {
        CreateLinkRequest request = new CreateLinkRequest(
                "https://example.com/" + "a".repeat(2029), OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), null);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void titleLongerThan100Characters_hasViolation() {
        CreateLinkRequest request = new CreateLinkRequest(
                "https://example.com", OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), "a".repeat(101));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void missingExpiresAt_hasViolation() {
        CreateLinkRequest request = new CreateLinkRequest("https://example.com", null, null);

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
