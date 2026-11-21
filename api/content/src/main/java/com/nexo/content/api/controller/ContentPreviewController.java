package com.nexo.content.api.controller;

import com.nexo.content.application.service.ContentPreviewService;
import com.nexo.content.domain.dto.response.ApiResponse;
import com.nexo.content.domain.dto.response.TopicContentSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Para cualquier usuario autenticado: qué tipo de contenido trae un tópico, antes de seguirlo. */
@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
@Validated
@Tag(name = "Content preview", description = "What a topic delivers, for readers deciding whether to follow it")
public class ContentPreviewController {

    private final ContentPreviewService previewService;

    @GetMapping("/{topicId}/content-summary")
    @Operation(summary = "Published items in the last 7 days and the most recent ones for a topic")
    public ResponseEntity<ApiResponse<TopicContentSummaryResponse>> summary(
            @PathVariable UUID topicId,
            @RequestParam(defaultValue = "3") @Min(1) @Max(10) int limit) {
        return ResponseEntity.ok(ApiResponse.ok(previewService.forTopic(topicId, limit)));
    }
}
