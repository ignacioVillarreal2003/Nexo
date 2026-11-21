package com.nexo.subscription.domain.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "nexo")
@Validated
public record ApplicationProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull UserEvents userEvents,
        @Valid @NotNull TopicService topicService,
        @Valid @NotNull Defaults defaults,
        @Valid @NotNull Monitoring monitoring) {

    public record Security(@Valid @NotNull JsonWebToken jwt, @Valid @NotNull Cors cors) {

        public record JsonWebToken(String jsonWebKeySetUri, String secret, String issuer) {

            public boolean hasJsonWebKeySetUri() {
                return jsonWebKeySetUri != null && !jsonWebKeySetUri.isBlank();
            }

            public boolean hasSecret() {
                return secret != null && !secret.isBlank();
            }
        }

        public record Cors(
                @NotNull List<String> allowedOrigins,
                @NotNull List<String> allowedMethods,
                @NotNull List<String> allowedHeaders,
                @NotNull List<String> exposedHeaders,
                boolean allowCredentials,
                long maxAge) {
        }
    }

    public record UserEvents(@NotBlank String topic, @NotBlank String deadLetterTopic, @Min(1) int replicas,
                             @Min(1) int concurrency) {
    }

    public record TopicService(@NotBlank String baseUrl, @NotNull Duration timeout) {
    }

    /** Valores con los que arranca un usuario que todavía no configuró su entrega. */
    public record Defaults(
            @Min(1) @Max(50) int dailyLimit,
            @Min(0) @Max(23) int deliveryHour,
            @NotBlank String timezone,
            @Min(1) @Max(200) int maximumDailyItems) {
    }

    public record Monitoring(@NotBlank String username, @NotBlank String password) {
    }
}
