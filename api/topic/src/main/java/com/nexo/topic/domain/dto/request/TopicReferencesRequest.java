package com.nexo.topic.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record TopicReferencesRequest(@NotNull @Size(max = 500) List<UUID> topicIds) {
}
