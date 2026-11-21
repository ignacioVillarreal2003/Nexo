package com.nexo.content.domain.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Contenidos elegidos para alguien: se encola su informe (si falta) y se devuelven los que ya están listos. */
public record ContentBriefsRequest(@NotNull @Size(min = 1, max = 200) List<@NotNull UUID> ids) {
}
