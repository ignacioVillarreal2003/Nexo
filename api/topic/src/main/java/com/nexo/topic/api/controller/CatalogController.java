package com.nexo.topic.api.controller;

import com.nexo.topic.application.service.CatalogService;
import com.nexo.topic.domain.dto.response.ApiResponse;
import com.nexo.topic.domain.dto.response.CategoryResponse;
import com.nexo.topic.domain.dto.response.TopicResponse;
import com.nexo.topic.domain.dto.response.TopicSourceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Catalog", description = "Public, read-only topic catalog")
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/categories")
    @Operation(summary = "Active categories with their active topics")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> categories() {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.catalog()));
    }

    @GetMapping("/topics")
    @Operation(summary = "Search active topics by text and category")
    public ResponseEntity<ApiResponse<List<TopicResponse>>> topics(@RequestParam(required = false) UUID categoryId,
                                                                   @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.search(categoryId, search)));
    }

    @GetMapping("/topics/{idOrSlug}")
    @Operation(summary = "A topic by id or slug")
    public ResponseEntity<ApiResponse<TopicResponse>> topic(@PathVariable String idOrSlug) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.get(idOrSlug)));
    }

    @GetMapping("/topics/{idOrSlug}/sources")
    @Operation(summary = "Active sources the topic is collected from")
    public ResponseEntity<ApiResponse<List<TopicSourceResponse>>> sources(@PathVariable String idOrSlug) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.sources(idOrSlug)));
    }
}
