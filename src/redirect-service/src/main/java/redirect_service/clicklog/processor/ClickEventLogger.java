package redirect_service.clicklog.processor;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import redirect_service.clicklog.event.ClickEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** 개발·운영 관측용 구조화 로그의 최종 소비자다. */
@Component
@RequiredArgsConstructor
public class ClickEventLogger {

    /** Firehose로 나가는 click-events.log 전용 — 매 줄이 순수 JSON이어야 한다. */
    private static final Logger CLICK_EVENT_LOG = LoggerFactory.getLogger("click.event");

    /** 직렬화 실패 등 운영 경고 — click-events.log를 오염시키지 않도록 별도로 남긴다. */
    private static final Logger OPERATIONAL_LOG = LoggerFactory.getLogger(ClickEventLogger.class);

    private final JsonMapper jsonMapper;

    @Async("clickEventLoggerExecutor")
    @EventListener
    public void onClick(ClickEvent clickEvent) {
        try {
            CLICK_EVENT_LOG.info(jsonMapper.writeValueAsString(clickEvent));
        } catch (JacksonException exception) {
            OPERATIONAL_LOG.warn("Click event could not be serialized", exception);
        }
    }
}
