package com.nexo.content.api.controller;

import com.nexo.content.application.service.CandidateService;
import com.nexo.content.application.service.ContentBriefService;
import com.nexo.content.domain.dto.request.ContentBriefsRequest;
import com.nexo.content.domain.dto.response.ContentBriefResponse;
import com.nexo.content.application.service.PipelineService;
import com.nexo.content.domain.dto.request.FinishRunRequest;
import com.nexo.content.domain.dto.request.KnownUrlsRequest;
import com.nexo.content.domain.dto.request.RecordErrorRequest;
import com.nexo.content.domain.dto.request.StartRunRequest;
import com.nexo.content.domain.dto.response.ApiResponse;
import com.nexo.content.domain.dto.response.CandidateResponse;
import com.nexo.content.domain.dto.response.RunStartedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Endpoints de servicio: el collector registra ejecuciones y errores; la entrega pide candidatos. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
@Tag(name = "Pipeline", description = "Service-to-service ingestion and delivery reads")
public class PipelineController {

    private final PipelineService pipelineService;
    private final CandidateService candidateService;
    private final ContentBriefService contentBriefService;

    @PostMapping("/pipeline-runs")
    @PreAuthorize("hasAuthority('CONTENT_INGEST')")
    @Operation(summary = "Open a collection run")
    public ResponseEntity<ApiResponse<RunStartedResponse>> start(@Valid @RequestBody StartRunRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                new RunStartedResponse(pipelineService.start(request.trigger(), request.topicsPlanned()).getId())));
    }

    @PostMapping("/pipeline-runs/{id}/finish")
    @PreAuthorize("hasAuthority('CONTENT_INGEST')")
    @Operation(summary = "Close a collection run")
    public ResponseEntity<ApiResponse<Void>> finish(@PathVariable UUID id, @Valid @RequestBody FinishRunRequest request) {
        pipelineService.finish(id, request.status(), request.itemsCollected(), request.notes());
        return ResponseEntity.ok(ApiResponse.message("Run finished"));
    }

    @PostMapping("/pipeline-errors")
    @PreAuthorize("hasAuthority('CONTENT_INGEST')")
    @Operation(summary = "Record an error of a pipeline stage")
    public ResponseEntity<ApiResponse<Void>> error(@Valid @RequestBody RecordErrorRequest request) {
        pipelineService.recordError(request.runId(), request.stage(), request.source(), request.topicId(), request.message());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.message("Error recorded"));
    }

    @PostMapping("/contents/known-urls")
    @PreAuthorize("hasAuthority('CONTENT_INGEST')")
    @Operation(summary = "Which of these URLs the platform already has")
    public ResponseEntity<ApiResponse<List<String>>> knownUrls(@Valid @RequestBody KnownUrlsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(candidateService.knownUrls(request.urls())));
    }

    @GetMapping("/contents/candidates")
    @PreAuthorize("hasAuthority('CONTENT_CANDIDATE_READ')")
    @Operation(summary = "Best recent published content of a topic, with embeddings")
    public ResponseEntity<ApiResponse<List<CandidateResponse>>> candidates(
            @RequestParam UUID topicId, @RequestParam(defaultValue = "60") @Min(1) @Max(200) int limit) {
        return ResponseEntity.ok(ApiResponse.ok(candidateService.candidates(topicId, limit)));
    }

    @PostMapping("/contents/briefs")
    @PreAuthorize("hasAuthority('CONTENT_CANDIDATE_READ')")
    @Operation(summary = "Queue the full brief of chosen contents and return the ones already written")
    public ResponseEntity<ApiResponse<List<ContentBriefResponse>>> briefs(@Valid @RequestBody ContentBriefsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(contentBriefService.request(request.ids())));
    }
}
