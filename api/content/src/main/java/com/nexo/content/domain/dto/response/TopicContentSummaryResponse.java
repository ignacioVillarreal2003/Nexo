package com.nexo.content.domain.dto.response;

import java.util.List;

/**
 * Lo que trae un tópico: cuántos contenidos se publicaron en la última semana y los más recientes.
 */
public record TopicContentSummaryResponse(long publishedLast7Days, List<ContentPreviewResponse> latest) {
}
