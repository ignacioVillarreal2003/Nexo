package com.nexo.content.infrastructure.client;

import com.nexo.content.domain.entity.enums.ContentRelation;
import com.nexo.content.domain.exception.ApiException;
import com.nexo.content.domain.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Cliente del servicio de IA (Python/FastAPI), autenticado con el token de servicio. */
@Component
@RequiredArgsConstructor
public class AiServiceClient {

    public record TopicContext(String name, String description, List<String> keywords) {
    }

    public record AnalysisRequest(TopicContext topic, String title, String text, String url,
                                  OffsetDateTime publishedAt, String language) {
    }

    public record Analysis(boolean relevant, double relevanceScore, double qualityScore, String title,
                           String summary, String titleEn, String summaryEn, List<String> keywords,
                           List<String> entities, String language, String mode, String reason, float[] embedding) {
    }

    public record BriefRequest(String title, String text, String url, String sourceName, String topicName,
                               OffsetDateTime publishedAt, boolean allowFallback) {
    }

    public record BriefResult(String title, String titleEn, String summary, String summaryEn, String detailedSummary,
                              String detailedSummaryEn, List<String> keyPoints, List<String> keyPointsEn,
                              String whyItMatters, String whyItMattersEn, Integer readingMinutes, Integer sourceChars,
                              String resolvedUrl, String imageUrl, String mode) {
    }

    private record BriefEnvelope(BriefResult data) {
    }

    public record ComparedItem(String title, String summary, OffsetDateTime publishedAt) {
    }

    public record ComparisonRequest(ComparedItem previous, ComparedItem candidate) {
    }

    /** Si el contenido nuevo es duplicado, actualización o información nueva respecto del anterior. */
    public record Comparison(ContentRelation verdict, String reason, String mode) {
    }

    private record AnalysisEnvelope(Analysis data) {
    }

    private record ComparisonEnvelope(Comparison data) {
    }

    private final RestClient aiServiceRestClient;
    private final ServiceTokenProvider serviceTokenProvider;

    @Retry(name = "ai-service")
    @CircuitBreaker(name = "ai-service")
    public Analysis analyze(AnalysisRequest request) {
        AnalysisEnvelope response = post("/api/v1/analyses", request, AnalysisEnvelope.class);
        if (response == null || response.data() == null) {
            throw unavailable(null);
        }
        return response.data();
    }

    @Retry(name = "ai-service")
    @CircuitBreaker(name = "ai-service")
    public Comparison compare(ComparisonRequest request) {
        ComparisonEnvelope response = post("/api/v1/comparisons", request, ComparisonEnvelope.class);
        return response == null ? null : response.data();
    }

    /**
     * Informe completo de una noticia (la IA lee la nota entera). Sin reintentos propios: si la IA no puede ahora
     * lanza la excepción y la cola de informes lo vuelve a intentar en la próxima pasada.
     */
    public BriefResult brief(BriefRequest request) {
        BriefEnvelope response = post("/api/v1/briefs", request, BriefEnvelope.class);
        if (response == null || response.data() == null) {
            throw unavailable(null);
        }
        return response.data();
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return send(path, body, type);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 401) {
                throw unavailable(e);
            }
            serviceTokenProvider.invalidate();
            try {
                return send(path, body, type);
            } catch (RestClientException retry) {
                throw unavailable(retry);
            }
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    private <T> T send(String path, Object body, Class<T> type) {
        return aiServiceRestClient.post()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceTokenProvider.token())
                .body(body)
                .retrieve()
                .body(type);
    }

    private static ApiException unavailable(Exception cause) {
        return new ApiException(ErrorCode.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                "The AI service is not available right now", Map.of(), cause);
    }
}
