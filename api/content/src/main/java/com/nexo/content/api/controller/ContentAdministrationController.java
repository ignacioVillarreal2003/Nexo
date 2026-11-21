package com.nexo.content.api.controller;

import com.nexo.content.api.security.CurrentUser;
import com.nexo.content.application.security.AuthenticatedUser;
import com.nexo.content.application.service.ContentReviewService;
import com.nexo.content.application.service.PipelineService;
import com.nexo.content.domain.dto.request.ReviewContentRequest;
import com.nexo.content.domain.dto.response.ApiResponse;
import com.nexo.content.domain.dto.response.ContentResponse;
import com.nexo.content.domain.dto.response.ContentStatisticsResponse;
import com.nexo.content.domain.dto.response.PageResponse;
import com.nexo.content.domain.entity.PipelineError;
import com.nexo.content.domain.entity.PipelineRun;
import com.nexo.content.domain.entity.enums.ContentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/administration")
@RequiredArgsConstructor
@Validated
@Tag(name = "Content administration", description = "Review collected content and monitor the pipeline")
public class ContentAdministrationController {

    private final ContentReviewService reviewService;
    private final PipelineService pipelineService;

    @GetMapping("/contents")
    @PreAuthorize("hasAuthority('CONTENT_REVIEW')")
    @Operation(summary = "Search collected content")
    public ResponseEntity<ApiResponse<PageResponse<ContentResponse>>> contents(
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) UUID topicId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.search(status, topicId, search, page, size)));
    }

    @GetMapping("/contents/statistics")
    @PreAuthorize("hasAuthority('STATISTICS_READ')")
    @Operation(summary = "Content totals by status and recent publications")
    public ResponseEntity<ApiResponse<ContentStatisticsResponse>> statistics() {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.statistics()));
    }

    @GetMapping("/contents/{id}")
    @PreAuthorize("hasAuthority('CONTENT_REVIEW')")
    @Operation(summary = "A collected content with its processing details")
    public ResponseEntity<ApiResponse<ContentResponse>> content(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.get(id)));
    }

    @PutMapping("/contents/{id}")
    @PreAuthorize("hasAuthority('CONTENT_REVIEW')")
    @Operation(summary = "Correct the title or summary, or publish, hide or reject a content")
    public ResponseEntity<ApiResponse<ContentResponse>> review(@PathVariable UUID id,
                                                               @Valid @RequestBody ReviewContentRequest request,
                                                               @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(reviewService.review(id, request, actor), "Content updated"));
    }

    @GetMapping("/pipeline-runs")
    @PreAuthorize("hasAuthority('PIPELINE_READ')")
    @Operation(summary = "Collection runs, newest first")
    public ResponseEntity<ApiResponse<PageResponse<PipelineRun>>> runs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.ok(pipelineService.runs(page, size)));
    }

    @GetMapping("/pipeline-errors")
    @PreAuthorize("hasAuthority('PIPELINE_READ')")
    @Operation(summary = "Pipeline errors, optionally of a single run")
    public ResponseEntity<ApiResponse<PageResponse<PipelineError>>> errors(
            @RequestParam(required = false) UUID runId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return ResponseEntity.ok(ApiResponse.ok(pipelineService.errors(runId, page, size)));
    }
}
