package com.nexo.content.infrastructure.config;

import com.nexo.content.domain.config.ApplicationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class ClientConfiguration {

    @Bean
    public RestClient authenticationServiceRestClient(RestClient.Builder builder, ApplicationProperties properties) {
        return client(builder, properties.authenticationService().baseUrl(), properties.authenticationService().timeout());
    }

    @Bean
    public RestClient aiServiceRestClient(RestClient.Builder builder, ApplicationProperties properties) {
        return client(builder, properties.aiService().baseUrl(), properties.aiService().timeout());
    }

    private static RestClient client(RestClient.Builder builder, String baseUrl, Duration timeout) {
        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(5))
                        .withReadTimeout(timeout)))
                .build();
    }
}
