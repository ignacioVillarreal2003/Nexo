package com.nexo.content.domain.dto.request;

import com.nexo.content.domain.entity.enums.ContentStatus;
import jakarta.validation.constraints.Size;

public record ReviewContentRequest(@Size(max = 500) String title, @Size(max = 5000) String summary,
                                   ContentStatus status) {
}
