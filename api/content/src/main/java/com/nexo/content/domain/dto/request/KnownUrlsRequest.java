package com.nexo.content.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record KnownUrlsRequest(@NotNull @Size(max = 1000) List<String> urls) {
}
