package com.nexo.subscription.api.controller;

import com.nexo.subscription.api.security.CurrentUser;
import com.nexo.subscription.application.security.AuthenticatedUser;
import com.nexo.subscription.application.service.SubscriptionService;
import com.nexo.subscription.domain.dto.request.SubscribeRequest;
import com.nexo.subscription.domain.dto.request.UpdateDeliverySettingsRequest;
import com.nexo.subscription.domain.dto.request.UpdateSubscriptionRequest;
import com.nexo.subscription.domain.dto.response.ApiResponse;
import com.nexo.subscription.domain.dto.response.DeliverySettingsResponse;
import com.nexo.subscription.domain.dto.response.SubscriptionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Subscriptions", description = "The current user's topics and daily delivery settings")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/subscriptions")
    @Operation(summary = "Topics the current user follows")
    public ResponseEntity<ApiResponse<List<SubscriptionResponse>>> mine(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.mine(user.id())));
    }

    @PostMapping("/subscriptions")
    @Operation(summary = "Follow a topic (or reactivate a cancelled subscription)")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> subscribe(@CurrentUser AuthenticatedUser user,
                                                                       @Valid @RequestBody SubscribeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                subscriptionService.subscribe(user, request.topicId(), request.dailyLimit()), "Subscribed"));
    }

    @PatchMapping("/subscriptions/{topicId}")
    @Operation(summary = "Change how many items of a topic arrive per day")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> update(@CurrentUser AuthenticatedUser user,
                                                                    @PathVariable UUID topicId,
                                                                    @Valid @RequestBody UpdateSubscriptionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.changeDailyLimit(user.id(), topicId, request.dailyLimit())));
    }

    @DeleteMapping("/subscriptions/{topicId}")
    @Operation(summary = "Stop following a topic")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@CurrentUser AuthenticatedUser user, @PathVariable UUID topicId) {
        subscriptionService.unsubscribe(user, topicId);
        return ResponseEntity.ok(ApiResponse.message("Unsubscribed"));
    }

    @GetMapping("/delivery-settings")
    @Operation(summary = "When and how much content the current user receives")
    public ResponseEntity<ApiResponse<DeliverySettingsResponse>> settings(@CurrentUser AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.settings(user.id())));
    }

    @PutMapping("/delivery-settings")
    @Operation(summary = "Update time zone, delivery hour and daily maximum")
    public ResponseEntity<ApiResponse<DeliverySettingsResponse>> updateSettings(
            @CurrentUser AuthenticatedUser user, @Valid @RequestBody UpdateDeliverySettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptionService.updateSettings(user.id(), request), "Settings saved"));
    }
}
