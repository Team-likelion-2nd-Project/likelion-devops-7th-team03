package redirect_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.firehose.FirehoseClient;

/**
 * 자격증명은 SDK 기본 체인이 처리한다 — EKS에서는 IRSA(redirect-service-sa)가 주입하는
 * AWS_ROLE_ARN / AWS_WEB_IDENTITY_TOKEN_FILE 환경변수를 자동으로 읽어간다.
 *
 * 리전은 자격증명과 달리 클라이언트 생성 시점에 즉시 확인되므로, AWS_REGION이 없는
 * 환경(로컬 테스트, CI)에서도 빈 생성 자체가 실패하지 않도록 명시적으로 지정한다.
 */
@Configuration(proxyBeanMethods = false)
public class FirehoseConfig {

    @Bean
    @Lazy
    public FirehoseClient firehoseClient() {
        return FirehoseClient.builder()
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "ap-southeast-1")))
                .build();
    }
}