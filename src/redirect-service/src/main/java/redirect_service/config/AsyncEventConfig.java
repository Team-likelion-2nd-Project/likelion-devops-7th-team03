package redirect_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
public class AsyncEventConfig {

    /** 모든 부가 이벤트 처리는 bounded 큐에서 실행하며, 포화되어도 302 응답을 막지 않는다. */
    @Bean(name = "clickEventEnrichmentExecutor")
    public Executor clickEventEnrichmentExecutor() {
        return executor("click-enrichment-", 2, 4, 10_000);
    }

    @Bean(name = "clickLogExecutor")
    public Executor clickLogExecutor() {
        return executor("click-log-", 1, 2, 10_000);
    }

    @Bean(name = "redisStatsExecutor")
    public Executor redisStatsExecutor() {
        return executor("redis-stats-", 1, 2, 10_000);
    }

    private Executor executor(String threadNamePrefix, int corePoolSize, int maxPoolSize, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
