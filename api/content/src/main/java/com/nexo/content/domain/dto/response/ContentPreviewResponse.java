package com.nexo.content.domain.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Vista previa de un contenido publicado, para mostrar qué trae un tópico antes de seguirlo. */
public record ContentPreviewResponse(UUID id, String title, String summary, String sourceName, String originalUrl,
                                     String imageUrl, Instant publishedAt) {
}
