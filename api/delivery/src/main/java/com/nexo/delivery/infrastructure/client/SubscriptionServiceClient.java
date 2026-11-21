package com.nexo.delivery.infrastructure.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionServiceClient {

    public record PlannedTopic(UUID topicId, int dailyLimit) {
    }

    public record DeliveryPlan(UUID userId, String timezone, int deliveryHour, int maximumDailyItems, boolean paused,
                               List<PlannedTopic> topics) {
    }

    private record PlanEnvelope(DeliveryPlan data) {
    }

    private record UsersEnvelope(List<UUID> data) {
    }

    private static final String SERVICE = "subscription service";

    private final RestClient subscriptionServiceRestClient;
    private final AuthorizedRequests authorizedRequests;

    @Retry(name = "subscription-service")
    @CircuitBreaker(name = "subscription-service")
    public DeliveryPlan plan(UUID userId) {
        PlanEnvelope envelope = authorizedRequests.execute(SERVICE, token -> subscriptionServiceRestClient.get()
                .uri("/api/v1/delivery-plans/{userId}", userId)
                .header(AuthorizedRequests.header(), token)
                .retrieve(), PlanEnvelope.class);
        return envelope == null ? null : envelope.data();
    }

    @Retry(name = "subscription-service")
    @CircuitBreaker(name = "subscription-service")
    public List<UUID> usersDueAt(Instant at) {
        UsersEnvelope envelope = authorizedRequests.execute(SERVICE, token -> subscriptionServiceRestClient.get()
                .uri(builder -> builder.path("/api/v1/delivery-plans/due").queryParam("at", at.toString()).build())
                .header(AuthorizedRequests.header(), token)
                .retrieve(), UsersEnvelope.class);
        return envelope == null || envelope.data() == null ? List.of() : envelope.data();
    }

    @Retry(name = "subscription-service")
    @CircuitBreaker(name = "subscription-service")
    public List<UUID> subscribers() {
        UsersEnvelope envelope = authorizedRequests.execute(SERVICE, token -> subscriptionServiceRestClient.get()
                .uri("/api/v1/delivery-plans/subscribers")
                .header(AuthorizedRequests.header(), token)
                .retrieve(), UsersEnvelope.class);
        return envelope == null || envelope.data() == null ? List.of() : envelope.data();
    }
}
