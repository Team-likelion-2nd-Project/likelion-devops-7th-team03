package redirect_service.redirect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class RedirectControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    @DisplayName("존재하고 활성화되어 있으며 만료되지 않은 링크는 302로 리다이렉트한다")
    void redirectsForExistingVisibleAndUnexpiredLink() throws Exception {
        insertLink("valid-link", "https://example.com/landing", true, null);

        mockMvc.perform(get("/valid-link"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/landing"))
                .andExpect(header().exists("Set-Cookie"));
    }

    @Test
    @DisplayName("존재하지 않는 슬러그는 404를 반환한다")
    void returnsNotFoundForMissingSlug() throws Exception {
        mockMvc.perform(get("/missing-link"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    @DisplayName("비활성화된 링크는 404를 반환한다")
    void returnsNotFoundForInvisibleLink() throws Exception {
        insertLink("hidden-link", "https://example.com/hidden", false, null);

        mockMvc.perform(get("/hidden-link"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("만료된 링크는 404를 반환한다")
    void returnsNotFoundForExpiredLink() throws Exception {
        insertLink("expired-link", "https://example.com/expired", true, LocalDateTime.now().minusSeconds(1));

        mockMvc.perform(get("/expired-link"))
                .andExpect(status().isNotFound());
    }

    private void insertLink(String slug, String originalUrl, boolean isVisible, LocalDateTime expiresAt) {
        jdbcTemplate.update(
                "insert into links (slug, original_url, is_visible, expires_at) values (?, ?, ?, ?)",
                slug,
                originalUrl,
                isVisible,
                expiresAt
        );
    }
}
