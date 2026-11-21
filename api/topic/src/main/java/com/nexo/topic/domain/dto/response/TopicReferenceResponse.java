package com.nexo.topic.domain.dto.response;

import com.nexo.topic.domain.entity.enums.CatalogStatus;

import java.util.UUID;

/** Referencia mínima a un tópico para otros servicios (nombre e icono para mostrar). */
public record TopicReferenceResponse(
        UUID id,
        String slug,
        String name,
        String icon,
        UUID categoryId,
        String categoryName,
        CatalogStatus status) {
}
