package com.nexo.topic.domain.config;

import jakarta.validation.Valid;
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
        @Valid @NotNull Catalog catalog,
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

    /** @param cacheTtl vida del catálogo público en Redis */
    public record Catalog(@NotNull Duration cacheTtl) {
    }

    public record Monitoring(@NotBlank String username, @NotBlank String password) {
    }
}
