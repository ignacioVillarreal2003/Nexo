package com.nexo.delivery.infrastructure.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pide al collector una recopilación limitada a ciertos tópicos (scope {@code COLLECTION_REQUEST}). */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorServiceClient {

    private final RestClient collectorServiceRestClient;
    private final AuthorizedRequests authorizedRequests;

    /**
     * @return {@code true} si el collector aceptó el pedido. Un 409 (ya hay una recopilación en curso)
     * o cualquier otro fallo devuelven {@code false}: es una mejora de experiencia, nunca un error para el usuario.
     */
    public boolean requestCollection(Collection<UUID> topicIds) {
        List<String> ids = topicIds.stream().map(UUID::toString).toList();
        try {
            authorizedRequests.execute("collector service", token -> collectorServiceRestClient.post()
                    .uri("/api/v1/collections")
                    .header(AuthorizedRequests.header(), token)
                    .body(Map.of("topicIds", ids))
                    .retrieve(), String.class);
            return true;
        } catch (RuntimeException exc) {
            log.info("Collector did not accept an on-demand collection for {} topics: {}", ids.size(), exc.getMessage());
            return false;
        }
    }
}
