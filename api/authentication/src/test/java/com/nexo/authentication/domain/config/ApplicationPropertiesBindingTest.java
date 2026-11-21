package com.nexo.authentication.domain.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class PropertiesConfiguration {
    }

    @Test
    void theShippedConfigurationBindsAndPassesValidation() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ApplicationProperties properties = context.getBean(ApplicationProperties.class);

            assertThat(properties.security().jwt().issuer()).isEqualTo("nexo-authentication");
            assertThat(properties.monitoring().username()).isEqualTo("prometheus");
            assertThat(properties.admin().email()).isEqualTo("admin@nexo.local");
            assertThat(properties.events().userTopic()).isEqualTo("nexo.authentication.user-events");
        });
    }

    @Test
    void aBlankNestedFieldStopsTheApplicationInsteadOfBindingSilently() {
        runner.withPropertyValues("nexo.security.jwt.issuer=")
                .run(context -> assertThat(context).hasFailed());
    }
}
