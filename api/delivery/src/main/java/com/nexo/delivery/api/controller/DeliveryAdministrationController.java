package com.nexo.delivery.api.controller;

import com.nexo.delivery.application.service.DeliveryService;
import com.nexo.delivery.application.service.FeedService;
import com.nexo.delivery.domain.dto.request.RunDeliveryRequest;
import com.nexo.delivery.domain.dto.response.ApiResponse;
import com.nexo.delivery.domain.dto.response.DeliveryRunResponse;
import com.nexo.delivery.domain.dto.response.DeliveryStatisticsResponse;
import com.nexo.delivery.domain.dto.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/administration")
@RequiredArgsConstructor
@Validated
@Tag(name = "Delivery administration", description = "Trigger deliveries and inspect their runs")
public class DeliveryAdministrationController {

    private final DeliveryService deliveryService;
    private final FeedService feedService;

    @PostMapping("/delivery-runs")
    @PreAuthorize("hasAuthority('DELIVERY_MANAGE')")
    @Operation(summary = "Deliver now to one user or to every subscribed user")
    public ResponseEntity<ApiResponse<DeliveryService.RunResult>> run(@RequestBody(required = false) RunDeliveryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(deliveryService.runManually(request == null ? null : request.userId())));
    }

    @GetMapping("/delivery-runs")
    @PreAuthorize("hasAuthority('DELIVERY_MANAGE')")
    @Operation(summary = "Delivery runs, newest first")
    public ResponseEntity<ApiResponse<PageResponse<DeliveryRunResponse>>> runs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok(feedService.runs(page, size)));
    }

    @GetMapping("/delivery-statistics")
    @PreAuthorize("hasAuthority('STATISTICS_READ')")
    @Operation(summary = "Deliveries, reads and favorites")
    public ResponseEntity<ApiResponse<DeliveryStatisticsResponse>> statistics() {
        return ResponseEntity.ok(ApiResponse.ok(feedService.statistics()));
    }
}
