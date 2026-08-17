package redirect_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.services.firehose.FirehoseClient;

/**
 * 자격증명은 SDK 기본 체인이 처리한다 — EKS에서는 IRSA(redirect-service-sa)가 주입하는
 * AWS_ROLE_ARN / AWS_WEB_IDENTITY_TOKEN_FILE 환경변수를 자동으로 읽어간다.
 *
 * @Lazy: 컨텍스트 기동 시점이 아니라 실제로 클릭 이벤트가 처음 발행될 때 생성되도록 지연시킨다.
 * 테스트 환경(CI 등)에는 AWS 리전/자격증명이 없어 즉시 생성 시 SdkClientException이 나는데,
 * 테스트는 실제로 클릭 이벤트를 발행하지 않으므로 지연 생성이면 이 빈 자체가 만들어지지 않는다.
 */
@Configuration(proxyBeanMethods = false)
public class FirehoseConfig {

    @Bean
    @Lazy
    public FirehoseClient firehoseClient() {
        return FirehoseClient.builder().build();
    }
}