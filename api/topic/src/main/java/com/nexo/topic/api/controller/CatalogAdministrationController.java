package com.nexo.topic.api.controller;

import com.nexo.topic.api.security.CurrentUser;
import com.nexo.topic.application.security.AuthenticatedUser;
import com.nexo.topic.application.service.CatalogAdministrationService;
import com.nexo.topic.domain.dto.request.CategoryRequest;
import com.nexo.topic.domain.dto.request.ChangeStatusRequest;
import com.nexo.topic.domain.dto.request.SourceRequest;
import com.nexo.topic.domain.dto.request.TopicRequest;
import com.nexo.topic.domain.dto.response.ApiResponse;
import com.nexo.topic.domain.dto.response.CategoryResponse;
import com.nexo.topic.domain.dto.response.SourceResponse;
import com.nexo.topic.domain.dto.response.TopicDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/administration")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TOPIC_MANAGE')")
@Tag(name = "Catalog administration", description = "Manage categories, topics and sources")
public class CatalogAdministrationController {

    private final CatalogAdministrationService administrationService;

    @GetMapping("/categories")
    @Operation(summary = "Every category, active or not, with its topics")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> categories() {
        return ResponseEntity.ok(ApiResponse.ok(administrationService.categories()));
    }

    @PostMapping("/categories")
    @Operation(summary = "Create a category")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request,
                                                                        @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(administrationService.saveCategory(null, request, actor), "Category created"));
    }

    @PutMapping("/categories/{id}")
    @Operation(summary = "Update a category")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(@PathVariable UUID id,
                                                                        @Valid @RequestBody CategoryRequest request,
                                                                        @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(administrationService.saveCategory(id, request, actor), "Category updated"));
    }

    @PatchMapping("/categories/{id}/status")
    @Operation(summary = "Activate or deactivate a category")
    public ResponseEntity<ApiResponse<CategoryResponse>> categoryStatus(@PathVariable UUID id,
                                                                        @Valid @RequestBody ChangeStatusRequest request,
                                                                        @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(administrationService.changeCategoryStatus(id, request.status(), actor)));
    }

    @GetMapping("/topics")
    @Operation(summary = "Every topic with its search configuration")
    public ResponseEntity<ApiResponse<List<TopicDetailResponse>>> topics() {
        return ResponseEntity.ok(ApiResponse.ok(administrationService.topics()));
    }

    @GetMapping("/topics/{id}")
    @Operation(summary = "A topic with its search configuration")
    public ResponseEntity<ApiResponse<TopicDetailResponse>> topic(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(administrationService.topic(id)));
    }

    @PostMapping("/topics")
    @Operation(summary = "Create a topic")
    public ResponseEntity<ApiResponse<TopicDetailResponse>> createTopic(@Valid @RequestBody TopicRequest request,
                                                                        @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(administrationService.saveTopic(null, request, actor), "Topic created"));
    }

    @PutMapping("/topics/{id}")
    @Operation(summary = "Update a topic, its search configuration and its sources")
    public ResponseEntity<ApiResponse<TopicDetailResponse>> updateTopic(@PathVariable UUID id,
                                                                        @Valid @RequestBody TopicRequest request,
                                                                        @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(administrationService.saveTopic(id, request, actor), "Topic updated"));
    }

    @PatchMapping("/topics/{id}/status")
    @Operation(summary = "Activate or deactivate a topic")
    public ResponseEntity<ApiResponse<TopicDetailResponse>> topicStatus(@PathVariable UUID id,
                                                                        @Valid @RequestBody ChangeStatusRequest request,
                                                                        @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(administrationService.changeTopicStatus(id, request.status(), actor)));
    }

    @GetMapping("/sources")
    @Operation(summary = "Every information source")
    public ResponseEntity<ApiResponse<List<SourceResponse>>> sources() {
        return ResponseEntity.ok(ApiResponse.ok(administrationService.sources()));
    }

    @PostMapping("/sources")
    @Operation(summary = "Create a source")
    public ResponseEntity<ApiResponse<SourceResponse>> createSource(@Valid @RequestBody SourceRequest request,
                                                                    @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(administrationService.saveSource(null, request, actor), "Source created"));
    }

    @PutMapping("/sources/{id}")
    @Operation(summary = "Update a source")
    public ResponseEntity<ApiResponse<SourceResponse>> updateSource(@PathVariable UUID id,
                                                                    @Valid @RequestBody SourceRequest request,
                                                                    @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(administrationService.saveSource(id, request, actor), "Source updated"));
    }

    @PatchMapping("/sources/{id}/status")
    @Operation(summary = "Activate or deactivate a source")
    public ResponseEntity<ApiResponse<SourceResponse>> sourceStatus(@PathVariable UUID id,
                                                                    @Valid @RequestBody ChangeStatusRequest request,
                                                                    @CurrentUser AuthenticatedUser actor) {
        return ResponseEntity.ok(ApiResponse.ok(administrationService.changeSourceStatus(id, request.status(), actor)));
    }
}
