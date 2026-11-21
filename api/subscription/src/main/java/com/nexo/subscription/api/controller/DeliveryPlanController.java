package com.nexo.subscription.api.controller;

import com.nexo.subscription.application.service.SubscriptionService;
import com.nexo.subscription.domain.dto.response.ApiResponse;
import com.nexo.subscription.domain.dto.response.DeliveryPlanResponse;
import com.nexo.subscription.domain.dto.response.SubscriptionStatisticsResponse;
import com.nexo.subscription.domain.dto.response.TopicSubscribersResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lecturas para el collector y el servicio de entrega (tokens de servicio) y estadísticas de administración. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Delivery plans", description = "Service-to-service subscription reads")
public class DeliveryPlanController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/delivery-plans/{userId}")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ_UNRESTRICTED')")
    @Operation(summary = "A user's active topics, daily limits and delivery settings")
    public ResponseEntity<ApiResponse<DeliveryPlanResponse>> plan(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.deliveryPlan(userId)));
    }

    @GetMapping("/delivery-plans/due")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ_UNRESTRICTED')")
    @Operation(summary = "Users whose local delivery hour is the hour of 'at'")
    public ResponseEntity<ApiResponse<List<UUID>>> due(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.usersDueAt(at)));
    }

    @GetMapping("/delivery-plans/subscribers")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ_UNRESTRICTED')")
    @Operation(summary = "Every user with at least one active subscription")
    public ResponseEntity<ApiResponse<List<UUID>>> subscribers() {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.subscribedUsers()));
    }

    @GetMapping("/subscribed-topics")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_READ_UNRESTRICTED')")
    @Operation(summary = "Topics with subscribers; the collector only gathers these")
    public ResponseEntity<ApiResponse<List<TopicSubscribersResponse>>> subscribedTopics() {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.subscribedTopics()));
    }

    @GetMapping("/administration/statistics")
    @PreAuthorize("hasAuthority('STATISTICS_READ')")
    @Operation(summary = "Subscription totals and most followed topics")
    public ResponseEntity<ApiResponse<SubscriptionStatisticsResponse>> statistics() {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.statistics()));
    }
}
