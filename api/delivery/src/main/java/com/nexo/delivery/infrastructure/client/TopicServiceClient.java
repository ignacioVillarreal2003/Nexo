package com.nexo.delivery.infrastructure.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TopicServiceClient {

    public record TopicReference(UUID id, String slug, String name, String icon) {
    }

    private record ReferencesEnvelope(List<TopicReference> data) {
    }

    private record ReferencesRequest(List<UUID> topicIds) {
    }

    private final RestClient topicServiceRestClient;
    private final AuthorizedRequests authorizedRequests;

    @Retry(name = "topic-service")
    @CircuitBreaker(name = "topic-service")
    public Map<UUID, TopicReference> references(Collection<UUID> topicIds) {
        if (topicIds.isEmpty()) {
            return Map.of();
        }
        ReferencesEnvelope envelope = authorizedRequests.execute("topic service", token -> topicServiceRestClient.post()
                .uri("/api/v1/topic-references")
                .header(AuthorizedRequests.header(), token)
                .body(new ReferencesRequest(List.copyOf(topicIds)))
                .retrieve(), ReferencesEnvelope.class);
        if (envelope == null || envelope.data() == null) {
            return Map.of();
        }
        return envelope.data().stream().collect(Collectors.toMap(TopicReference::id, Function.identity()));
    }
}
