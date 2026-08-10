package redirect_service.clicklog.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import redirect_service.clicklog.ClickEventLog;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j(topic = "click.event")
@RequiredArgsConstructor
public class JsonLogClickEventPublisher implements ClickEventPublisher {

    private final JsonMapper jsonMapper;

    @Override
    public void publish(ClickEventLog clickEvent) {
        try {
            log.info("click_event={}", jsonMapper.writeValueAsString(clickEvent));
        } catch (JacksonException exception) {
            log.warn("Click event could not be serialized", exception);
        }
    }
}
