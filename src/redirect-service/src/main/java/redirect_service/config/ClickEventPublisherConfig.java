package redirect_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redirect_service.clicklog.publisher.ClickEventPublisher;
import redirect_service.clicklog.publisher.JsonLogClickEventPublisher;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class ClickEventPublisherConfig {

    // 우선순위가 낮음: 다른 ClickEventPublisher 빈이 스프링 컨테이너에 등록되지 않았을 때만 이게 생성됨
    @Bean
    @ConditionalOnMissingBean(ClickEventPublisher.class)
    public ClickEventPublisher defaultClickEventPublisher(JsonMapper jsonMapper) {
        return new JsonLogClickEventPublisher(jsonMapper);
    }
}
