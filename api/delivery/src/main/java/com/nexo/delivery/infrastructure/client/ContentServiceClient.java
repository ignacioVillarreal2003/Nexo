package com.nexo.delivery.infrastructure.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ContentServiceClient {

    public record Candidate(UUID id, String title, String summary, String titleEn, String summaryEn,
                            String originalUrl, String sourceName,
                            String imageUrl, Instant publicationDate, Instant collectedAt, String relation,
                            UUID relatedContentId, double score, float[] embedding) {

        /** Content la marcó como actualización de algo publicado antes ({@code relatedContentId}). */
        public boolean isUpdate() {
            return "UPDATE".equals(relation);
        }
    }

    private record CandidatesEnvelope(List<Candidate> data) {
    }

    /** Informe completo de un contenido (solo vienen los que ya están escritos). */
    public record ContentBrief(UUID id, String title, String summary, String titleEn, String summaryEn,
                               String detailedSummary, String detailedSummaryEn, List<String> keyPoints,
                               List<String> keyPointsEn, String whyItMatters, String whyItMattersEn,
                               Integer readingMinutes, String imageUrl, String originalUrl) {
    }

    private record BriefsEnvelope(List<ContentBrief> data) {
    }

    private final RestClient contentServiceRestClient;
    private final AuthorizedRequests authorizedRequests;

    @Retry(name = "content-service")
    @CircuitBreaker(name = "content-service")
    public List<Candidate> candidates(UUID topicId, int limit) {
        CandidatesEnvelope envelope = authorizedRequests.execute("content service", token -> contentServiceRestClient.get()
                .uri(builder -> builder.path("/api/v1/contents/candidates")
                        .queryParam("topicId", topicId)
                        .queryParam("limit", Math.min(limit, 200))
                        .build())
                .header(AuthorizedRequests.header(), token)
                .retrieve(), CandidatesEnvelope.class);
        return envelope == null || envelope.data() == null ? List.of() : envelope.data();
    }

    /**
     * Pide el informe completo de estos contenidos (content lo escribe en segundo plano si falta) y devuelve los que
     * ya están listos.
     */
    public List<ContentBrief> briefs(List<UUID> contentIds) {
        BriefsEnvelope envelope = authorizedRequests.execute("content service", token -> contentServiceRestClient.post()
                .uri("/api/v1/contents/briefs")
                .header(AuthorizedRequests.header(), token)
                .body(Map.of("ids", contentIds))
                .retrieve(), BriefsEnvelope.class);
        return envelope == null || envelope.data() == null ? List.of() : envelope.data();
    }
}
