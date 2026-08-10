package redirect_service.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    @DisplayName("서비스 상태 UP을 200으로 반환한다")
    void returnsUpStatus() {
        HealthController controller = new HealthController();

        var response = controller.health();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }
}
