package com.nexo.content.domain.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
        @Valid @NotNull Collected collected,
        @Valid @NotNull AuthenticationService authenticationService,
        @Valid @NotNull AiService aiService,
        @Valid @NotNull Deduplication deduplication,
        @Valid @NotNull Candidates candidates,
        @Valid @NotNull Runs runs,
        @Valid @NotNull Briefs briefs,
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

    /** Tópico de Kafka donde el collector publica cada documento recopilado. */
    public record Collected(@NotBlank String topic, @NotBlank String deadLetterTopic, @Min(1) int partitions,
                            @Min(1) int replicas, @Min(1) int concurrency) {
    }

    public record AuthenticationService(@NotBlank String baseUrl, String clientId, String clientSecret,
                                        @NotNull Duration timeout) {

        public boolean enabled() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    public record AiService(@NotBlank String baseUrl, @NotNull Duration timeout) {
    }

    /**
     * Umbrales del sistema antirrepetición.
     *
     * @param semanticDuplicateThreshold similitud a partir de la cual dos contenidos son el mismo
     * @param semanticRelatedThreshold   similitud a partir de la cual la IA distingue duplicado, actualización o nuevo
     * @param simhashMaximumDistance     distancia de Hamming máxima entre textos casi idénticos
     * @param window                     período hacia atrás en el que se buscan duplicados
     * @param minimumQuality             calidad mínima (0–1) para publicar
     */
    public record Deduplication(
            @DecimalMin("0.0") @DecimalMax("1.0") double semanticDuplicateThreshold,
            @DecimalMin("0.0") @DecimalMax("1.0") double semanticRelatedThreshold,
            @Min(0) int simhashMaximumDistance,
            @NotNull Duration window,
            @DecimalMin("0.0") @DecimalMax("1.0") double minimumQuality) {
    }

    /** @param window antigüedad máxima del contenido que se ofrece para las entregas */
    public record Candidates(@NotNull Duration window) {
    }

    /** @param staleAfter una ejecución que sigue en curso pasado este tiempo se marca como fallida */
    public record Runs(@NotNull Duration staleAfter) {
    }

    /**
     * Cola del informe completo.
     *
     * @param perRun          informes que se escriben por pasada (cuida la cuota de la IA)
     * @param maximumAttempts intentos fallidos antes de darlo por perdido
     * @param fallbackAfter   pasado este tiempo esperando se acepta el informe extractivo (sin IA)
     */
    public record Briefs(@Min(1) int perRun, @Min(1) int maximumAttempts, @NotNull Duration fallbackAfter) {
    }

    public record Monitoring(@NotBlank String username, @NotBlank String password) {
    }
}
