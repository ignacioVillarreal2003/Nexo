package com.nexo.topic.api.controller;

import com.nexo.topic.application.service.CatalogService;
import com.nexo.topic.domain.dto.request.TopicReferencesRequest;
import com.nexo.topic.domain.dto.response.ApiResponse;
import com.nexo.topic.domain.dto.response.CollectionPlanItemResponse;
import com.nexo.topic.domain.dto.response.TopicReferenceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Endpoints para otros servicios, autenticados con un token de servicio (client_credentials). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Collection plan", description = "Service-to-service catalog reads")
public class CollectionPlanController {

    private final CatalogService catalogService;

    @GetMapping("/collection-plan")
    @PreAuthorize("hasAuthority('TOPIC_PLAN_READ')")
    @Operation(summary = "Active topics with their active sources, for the collector")
    public ResponseEntity<ApiResponse<List<CollectionPlanItemResponse>>> plan(
            @RequestParam(required = false) List<UUID> topicIds) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.collectionPlan(topicIds)));
    }

    @PostMapping("/topic-references")
    @PreAuthorize("hasAuthority('TOPIC_PLAN_READ')")
    @Operation(summary = "Name, icon and category of several topics at once")
    public ResponseEntity<ApiResponse<List<TopicReferenceResponse>>> references(
            @Valid @RequestBody TopicReferencesRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.references(request.topicIds())));
    }
}
