package com.nexo.delivery.domain.config;

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
        @Valid @NotNull UserEvents userEvents,
        @Valid @NotNull AuthenticationService authenticationService,
        @Valid @NotNull Downstream subscriptionService,
        @Valid @NotNull Downstream topicService,
        @Valid @NotNull Downstream contentService,
        @Valid @NotNull CollectorService collectorService,
        @Valid @NotNull Events events,
        @Valid @NotNull Selection selection,
        @Valid @NotNull Digest digest,
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

    public record UserEvents(@NotBlank String topic, @NotBlank String deadLetterTopic, @Min(1) int replicas,
                             @Min(1) int concurrency) {
    }

    public record AuthenticationService(@NotBlank String baseUrl, String clientId, String clientSecret,
                                        @NotNull Duration timeout) {

        public boolean enabled() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }

    /**
     * Collector: recopilación a pedido de los tópicos que un usuario sigue y todavía no tienen contenido.
     * {@code requestCooldown} evita pedir el mismo tópico más de una vez en ese lapso.
     */
    public record CollectorService(@NotBlank String baseUrl, @NotNull Duration timeout, boolean onDemand,
                                   @NotNull Duration requestCooldown) {
    }

    public record Downstream(@NotBlank String baseUrl, @NotNull Duration timeout) {
    }

    public record Events(@NotBlank String notificationCommandsTopic, @NotNull Duration relayInterval,
                         @Min(1) int relayBatchSize, @Min(1) int relayMaxAttempts) {
    }

    /**
     * @param userSimilarityThreshold similitud con el historial del usuario a partir de la cual algo cuenta como repetido
     * @param historyWindow           período del historial contra el que se compara
     * @param candidateFactor         candidatos pedidos por cada contenido a entregar
     * @param workers                 usuarios procesados en paralelo
     * @param schedule                cron (UTC) de la entrega horaria
     */
    public record Selection(
            @DecimalMin("0.0") @DecimalMax("1.0") double userSimilarityThreshold,
            @NotNull Duration historyWindow,
            @Min(1) int candidateFactor,
            @Min(1) int workers,
            @NotBlank String schedule) {
    }

    /**
     * Correos de resumen.
     *
     * @param automaticItems       tope del resumen automático (lo que todavía no salió por mail)
     * @param onDemandItems        tope de "Enviarme por mail" (todo el día); mantiene el correo por debajo de
     *                             ~100 KB, donde Gmail lo recorta
     * @param maximumWait          cuánto se espera a que las noticias tengan su informe completo
     * @param detailedSummaryChars largo máximo del resumen completo de cada noticia dentro del correo
     * @param perRun               pedidos de correo que se atienden por pasada
     */
    public record Digest(@Min(1) int automaticItems, @Min(1) int onDemandItems, @NotNull Duration maximumWait,
                         @Min(200) int detailedSummaryChars, @Min(1) int perRun) {
    }

    /**
     * Copia del informe completo en lo entregado.
     *
     * @param window    antigüedad máxima de lo entregado que todavía espera su informe
     * @param batchSize contenidos por consulta a content
     */
    public record Briefs(@NotNull Duration window, @Min(1) int batchSize) {
    }

    public record Monitoring(@NotBlank String username, @NotBlank String password) {
    }
}
