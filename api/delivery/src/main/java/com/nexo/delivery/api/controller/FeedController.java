package com.nexo.delivery.api.controller;

import com.nexo.delivery.api.security.CurrentUser;
import com.nexo.delivery.application.security.AuthenticatedUser;
import com.nexo.delivery.application.service.DeliveryService;
import com.nexo.delivery.application.service.FeedService;
import com.nexo.delivery.application.service.OnDemandCollectionService;
import com.nexo.delivery.domain.dto.response.ApiResponse;
import com.nexo.delivery.domain.dto.response.DayResponse;
import com.nexo.delivery.domain.dto.response.EmailDigestResponse;
import com.nexo.delivery.domain.dto.response.FeedItemResponse;
import com.nexo.delivery.domain.dto.response.PageResponse;
import com.nexo.delivery.domain.dto.response.RefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@Validated
@Tag(name = "Feed", description = "My day: delivered content, history, favorites and reads")
public class FeedController {

    private final FeedService feedService;
    private final DeliveryService deliveryService;
    private final OnDemandCollectionService onDemandCollection;

    @GetMapping
    @Operation(summary = "Content of a day (today in the user's time zone by default)")
    public ResponseEntity<ApiResponse<DayResponse>> day(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID topicId) {
        LocalDate today = deliveryService.todayFor(user.id());
        return ResponseEntity.ok(ApiResponse.ok(feedService.day(user.id(), date == null ? today : date, today, topicId)));
    }

    @GetMapping("/history")
    @Operation(summary = "Everything the user received, searchable and filterable")
    public ResponseEntity<ApiResponse<PageResponse<FeedItemResponse>>> history(
            @CurrentUser AuthenticatedUser user,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean favorites,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok(feedService.history(user.id(), topicId, from, to, read, search, favorites, page, size)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Complete today's delivery with topics that have not received content yet")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(@CurrentUser AuthenticatedUser user) {
        DeliveryService.UserResult result = deliveryService.deliverFor(user.id());
        boolean collecting = onDemandCollection.requestFor(result.topicsWithoutContent());
        return ResponseEntity.ok(ApiResponse.ok(new RefreshResponse(result.delivered(), collecting)));
    }

    @PostMapping("/email")
    @Operation(summary = "Email today's delivered items, every time it is requested")
    public ResponseEntity<ApiResponse<EmailDigestResponse>> email(@CurrentUser AuthenticatedUser user) {
        DeliveryService.EmailResult result = deliveryService.emailDay(user.id());
        boolean collecting = onDemandCollection.requestFor(result.topicsWithoutContent());
        return ResponseEntity.ok(ApiResponse.ok(new EmailDigestResponse(result.emailed(), collecting)));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a delivered item as read")
    public ResponseEntity<ApiResponse<Void>> read(@CurrentUser AuthenticatedUser user, @PathVariable UUID id) {
        feedService.markRead(user.id(), id, true);
        return ResponseEntity.ok(ApiResponse.message("Marked as read"));
    }

    @DeleteMapping("/{id}/read")
    @Operation(summary = "Mark a delivered item as unread")
    public ResponseEntity<ApiResponse<Void>> unread(@CurrentUser AuthenticatedUser user, @PathVariable UUID id) {
        feedService.markRead(user.id(), id, false);
        return ResponseEntity.ok(ApiResponse.message("Marked as unread"));
    }

    @PostMapping("/{id}/favorite")
    @Operation(summary = "Save a delivered item to favorites")
    public ResponseEntity<ApiResponse<Void>> favorite(@CurrentUser AuthenticatedUser user, @PathVariable UUID id) {
        feedService.markFavorite(user.id(), id, true);
        return ResponseEntity.ok(ApiResponse.message("Saved to favorites"));
    }

    @DeleteMapping("/{id}/favorite")
    @Operation(summary = "Remove a delivered item from favorites")
    public ResponseEntity<ApiResponse<Void>> unfavorite(@CurrentUser AuthenticatedUser user, @PathVariable UUID id) {
        feedService.markFavorite(user.id(), id, false);
        return ResponseEntity.ok(ApiResponse.message("Removed from favorites"));
    }
}
