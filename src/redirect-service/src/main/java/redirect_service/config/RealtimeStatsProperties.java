package redirect_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.realtime-stats")
public class RealtimeStatsProperties {

    /** Redis 키를 기준일 자정(KST)부터 이 기간만큼 보관한다. */
    private Duration retention = Duration.ofDays(3);
    /** 5초 배치로 보낼 이벤트를 저장하는 bounded 버퍼의 최대 크기다. */
    private int bufferCapacity = 100_000;
}
