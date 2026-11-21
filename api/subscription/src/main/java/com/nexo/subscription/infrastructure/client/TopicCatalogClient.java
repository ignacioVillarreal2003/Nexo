package com.nexo.subscription.infrastructure.client;

import com.nexo.subscription.domain.exception.ApiException;
import com.nexo.subscription.domain.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lee el catálogo público del servicio de tópicos (no requiere token). Se usa para validar
 * suscripciones y para mostrar el nombre e icono de cada tópico suscrito.
 */
@Component
@RequiredArgsConstructor
public class TopicCatalogClient {

    public record TopicSummary(UUID id, String slug, String name, String icon, String categoryName) {
    }

    private record CategoriesEnvelope(List<CategoryEntry> data) {
    }

    private record CategoryEntry(String name, List<TopicEntry> topics) {
    }

    private record TopicEntry(UUID id, String slug, String name, String icon) {
    }

    private final RestClient topicServiceRestClient;

    @Retry(name = "topic-service")
    @CircuitBreaker(name = "topic-service")
    public Map<UUID, TopicSummary> publishedTopics() {
        try {
            CategoriesEnvelope response = topicServiceRestClient.get()
                    .uri("/api/v1/categories")
                    .retrieve()
                    .body(CategoriesEnvelope.class);
            if (response == null || response.data() == null) {
                return Map.of();
            }
            return response.data().stream()
                    .flatMap(category -> category.topics().stream()
                            .map(t -> new TopicSummary(t.id(), t.slug(), t.name(), t.icon(), category.name())))
                    .collect(Collectors.toMap(TopicSummary::id, Function.identity(), (a, b) -> a));
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    "The topic catalog is not available right now", Map.of(), e);
        }
    }
}
