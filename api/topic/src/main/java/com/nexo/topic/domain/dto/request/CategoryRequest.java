package com.nexo.topic.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 80) String slug,
        @Size(max = 2000) String description,
        @Size(max = 40) String icon,
        @Size(max = 500) String imageUrl,
        int sortOrder) {
}
