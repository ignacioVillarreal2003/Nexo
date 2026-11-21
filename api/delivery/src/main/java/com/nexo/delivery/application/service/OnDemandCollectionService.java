package com.nexo.delivery.application.service;

import com.nexo.delivery.domain.config.ApplicationProperties;
import com.nexo.delivery.infrastructure.client.CollectorServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recopilación a pedido: cuando un usuario pide su día y alguno de sus tópicos todavía no tiene contenido
 * (típicamente porque lo empezó a seguir hoy), se le pide al collector que recopile solo esos tópicos
 * en vez de esperar a la recopilación diaria. Cada tópico se pide como mucho una vez por {@code requestCooldown}.
 */
@Service
public class OnDemandCollectionService {

    private final CollectorServiceClient collectorClient;
    private final ApplicationProperties properties;
    private final Clock clock;
    private final Map<UUID, Instant> lastRequested = new ConcurrentHashMap<>();

    @Autowired
    public OnDemandCollectionService(CollectorServiceClient collectorClient, ApplicationProperties properties) {
        this(collectorClient, properties, Clock.systemUTC());
    }

    OnDemandCollectionService(CollectorServiceClient collectorClient, ApplicationProperties properties, Clock clock) {
        this.collectorClient = collectorClient;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return {@code true} si hay (desde ahora o desde hace poco) una recopilación en marcha para esos tópicos. */
    public boolean requestFor(Collection<UUID> topicIds) {
        if (!properties.collectorService().onDemand() || topicIds.isEmpty()) {
            return false;
        }
        Instant now = clock.instant();
        Instant threshold = now.minus(properties.collectorService().requestCooldown());
        List<UUID> due = topicIds.stream()
                .filter(id -> lastRequested.getOrDefault(id, Instant.MIN).isBefore(threshold))
                .toList();
        if (due.isEmpty()) {
            return true; // ya se pidió hace poco: el contenido está en camino
        }
        boolean accepted = collectorClient.requestCollection(due);
        if (accepted) {
            due.forEach(id -> lastRequested.put(id, now));
        }
        return accepted;
    }
}
