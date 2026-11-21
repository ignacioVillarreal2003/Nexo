package com.nexo.topic.domain.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import com.nexo.topic.domain.entity.enums.TopicKind;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TopicRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 80) String slug,
        @Size(max = 2000) String description,
        @Size(max = 40) String icon,
        @Size(max = 500) String imageUrl,
        @Size(max = 30) List<@NotBlank @Size(max = 80) String> keywords,
        @Size(max = 500) String searchQuery,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal minimumRelevance,
        @Min(1) @Max(200) Integer maximumItemsPerRun,
        @Size(max = 200) String arxivCategories,
        List<UUID> sourceIds,
        TopicKind kind,
        @Pattern(regexp = "^$|[A-Za-z]{2}", message = "Country code must have two letters (ISO 3166-1)") String countryCode) {
}
