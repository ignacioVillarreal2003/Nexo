package com.nexo.subscription.infrastructure.config;

import com.nexo.subscription.domain.config.ApplicationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class TopicServiceClientConfiguration {

    @Bean
    public RestClient topicServiceRestClient(RestClient.Builder builder, ApplicationProperties properties) {
        ApplicationProperties.TopicService config = properties.topicService();
        return builder.clone()
                .baseUrl(config.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(config.timeout())
                        .withReadTimeout(config.timeout())))
                .build();
    }
}
