package redirect_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.firehose.FirehoseClient;

/**
 * 자격증명은 SDK 기본 체인이 처리한다 — EKS에서는 IRSA(redirect-service-sa)가 주입하는
 * AWS_ROLE_ARN / AWS_WEB_IDENTITY_TOKEN_FILE 환경변수를 자동으로 읽어간다.
 */
@Configuration(proxyBeanMethods = false)
public class FirehoseConfig {

    @Bean
    public FirehoseClient firehoseClient() {
        return FirehoseClient.builder().build();
    }
}
