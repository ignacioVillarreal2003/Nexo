package com.nexo.content.domain.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Contenido publicado ofrecido al servicio de entrega, con su embedding para el control por usuario. */
public record CandidateResponse(
        UUID id,
        String title,
        String summary,
        String titleEn,
        String summaryEn,
        String originalUrl,
        String sourceName,
        String imageUrl,
        Instant publicationDate,
        Instant collectedAt,
        String relation,
        UUID relatedContentId,
        double score,
        float[] embedding) {
}
