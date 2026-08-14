package redirect_service.clicklog.processor;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import redirect_service.clicklog.event.ClickEvent;
import redirect_service.config.RealtimeStatsRecorderProperties;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RealtimeStatsRecorderTest {

    @Test
    void usesTheSharedDailyLinkStatsKeyContract() {
        LocalDate date = LocalDate.of(2026, 8, 13);

        assertThat(RealtimeStatsRecorder.clicksKey(date, 123L))
                .isEqualTo("stats:2026-08-13:link:123:clicks");
        assertThat(RealtimeStatsRecorder.uniqueVisitorsKey(date, 123L))
                .isEqualTo("stats:2026-08-13:link:123:uv");
    }

    @Test
    void dropsEventsWhenBoundedBufferIsFull() {
        RealtimeStatsRecorderProperties properties = new RealtimeStatsRecorderProperties();
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
