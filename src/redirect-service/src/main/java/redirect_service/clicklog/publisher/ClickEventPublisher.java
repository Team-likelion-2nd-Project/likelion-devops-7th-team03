package redirect_service.clicklog.publisher;

import redirect_service.clicklog.ClickEventLog;

/** 클릭 이벤트의 최종 전달 대상을 추상화한다. */
public interface ClickEventPublisher {

    void publish(ClickEventLog clickEvent);
}
