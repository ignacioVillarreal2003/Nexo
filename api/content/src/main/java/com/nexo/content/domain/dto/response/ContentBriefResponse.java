package com.nexo.content.domain.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Informe completo de un contenido, en español y en inglés, junto con el título y el resumen corto (que el informe
 * puede haber mejorado) y la imagen y el enlace (que pueden haberse completado al leer la nota).
 */
public record ContentBriefResponse(
        UUID id,
        String title,
        String summary,
        String titleEn,
        String summaryEn,
        String detailedSummary,
        String detailedSummaryEn,
        List<String> keyPoints,
        List<String> keyPointsEn,
        String whyItMatters,
        String whyItMattersEn,
        Integer readingMinutes,
        String imageUrl,
        String originalUrl) {
}
