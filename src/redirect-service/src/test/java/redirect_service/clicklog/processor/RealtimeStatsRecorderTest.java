package redirect_service.clicklog.processor;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import redirect_service.clicklog.event.ClickEvent;
import redirect_service.config.RealtimeStatsProperties;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RealtimeStatsRecorderTest {

    @Test
    void dropsEventsWhenBoundedBufferIsFull() {
        RealtimeStatsProperties properties = new RealtimeStatsProperties();
        properties.setBufferCapacity(1);
        RealtimeStatsRecorder handler =
                new RealtimeStatsRecorder(mock(StringRedisTemplate.class), properties);
        ClickEvent event = new ClickEvent(1L, LocalDateTime.now(ZoneOffset.UTC), "visitor-id",
                null, null, null, null, null, null, null, null);

        handler.onClick(event);
        handler.onClick(event);

        assertThat(handler.droppedEventCount()).isEqualTo(1);
    }
}
