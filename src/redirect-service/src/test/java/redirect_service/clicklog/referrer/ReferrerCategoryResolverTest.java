package redirect_service.clicklog.referrer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import redirect_service.clicklog.resolver.ReferrerCategoryResolver;

import static org.assertj.core.api.Assertions.assertThat;

class ReferrerCategoryResolverTest {

    private final ReferrerCategoryResolver resolver = new ReferrerCategoryResolver();

    @ParameterizedTest
    @CsvSource({
            "https://www.google.com/search, GOOGLE",
            "https://www.instagram.com/p/example, INSTAGRAM",
            "https://search.naver.com/search.naver, NAVER",
            "https://example.com, ETC"
    })
    @DisplayName("Referer 도메인을 유입 경로 카테고리로 분류한다")
    void categorizesReferrer(String referrer, String expectedCategory) {
        assertThat(resolver.resolve(referrer)).isEqualTo(expectedCategory);
    }

    @ParameterizedTest
    @CsvSource(value = {"'', DIRECT", "invalid-url, ETC"})
    @DisplayName("Referer가 없거나 형식이 잘못되면 기본 카테고리를 반환한다")
    void returnsFallbackCategory(String referrer, String expectedCategory) {
        assertThat(resolver.resolve(referrer)).isEqualTo(expectedCategory);
    }
}
