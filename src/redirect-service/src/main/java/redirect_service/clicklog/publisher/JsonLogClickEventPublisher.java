package redirect_service.clicklog.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redirect_service.clicklog.ClickEventLog;

@Slf4j(topic = "click.event")
@Component
@RequiredArgsConstructor
public class JsonLogClickEventPublisher implements ClickEventPublisher {

    private final ObjectMapper objectMapper;

    @Override
    public void publish(ClickEventLog clickEvent) {
        try {
            log.info("click_event={}", objectMapper.writeValueAsString(clickEvent));
        } catch (JsonProcessingException exception) {
            log.warn("Click event could not be serialized", exception);
        }
    }
}
