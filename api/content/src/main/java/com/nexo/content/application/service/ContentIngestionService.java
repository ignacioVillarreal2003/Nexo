package com.nexo.content.application.service;

import com.nexo.content.domain.config.ApplicationProperties;
import com.nexo.content.domain.entity.Content;
import com.nexo.content.domain.entity.enums.AiMode;
import com.nexo.content.domain.entity.enums.ContentRelation;
import com.nexo.content.domain.entity.enums.ContentStatus;
import com.nexo.content.domain.entity.enums.PipelineStage;
import com.nexo.content.domain.entity.enums.RunCounter;
import com.nexo.content.domain.util.DeduplicationPolicy;
import com.nexo.content.domain.util.DocumentFingerprint;
import com.nexo.content.domain.util.Vectors;

import static com.nexo.content.domain.util.Texts.firstNonBlank;
import static com.nexo.content.domain.util.Texts.truncate;
import com.nexo.content.infrastructure.client.AiServiceClient;
import com.nexo.content.infrastructure.client.AiServiceClient.Analysis;
import com.nexo.content.infrastructure.messaging.ContentCollectedEvent;
import com.nexo.content.infrastructure.repository.ContentRepository;
import com.nexo.content.infrastructure.repository.ContentRepository.Neighbor;
import com.nexo.content.infrastructure.repository.ContentTopicRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Procesa cada documento recopilado. Primero los niveles de deduplicación que no cuestan IA
 * (exacto y textual); después el análisis con IA (relevancia, calidad, título, resumen, embedding)
 * y la deduplicación semántica contra todo lo publicado recientemente.
 */
@Service
@Slf4j
public class ContentIngestionService {

    public enum Outcome { PUBLISHED, UPDATE, DUPLICATE, REJECTED }

    private static final int MAXIMUM_TEXT_CHARS = 12_000;
    /** Lo que se guarda de la nota completa (base del informe que se escribe al elegirla). */
    private static final int MAXIMUM_BODY_CHARS = 40_000;
    private static final int EXCERPT_CHARS = 1_500;
    private static final double DEFAULT_MINIMUM_RELEVANCE = 0.55;

    private final ContentRepository contentRepository;
    private final ContentTopicRepository contentTopicRepository;
    private final AiServiceClient aiServiceClient;
    private final PipelineService pipelineService;
    private final ApplicationProperties properties;
    private final DeduplicationPolicy policy;
    private final TransactionTemplate transactionTemplate;

    public ContentIngestionService(ContentRepository contentRepository, ContentTopicRepository contentTopicRepository,
                                   AiServiceClient aiServiceClient, PipelineService pipelineService,
                                   ApplicationProperties properties, TransactionTemplate transactionTemplate) {
        this.contentRepository = contentRepository;
        this.contentTopicRepository = contentTopicRepository;
        this.aiServiceClient = aiServiceClient;
        this.pipelineService = pipelineService;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.policy = new DeduplicationPolicy(properties.deduplication().semanticDuplicateThreshold(),
                properties.deduplication().semanticRelatedThreshold());
    }

    public void ingest(ContentCollectedEvent event) {
        if (!event.isComplete()) {
            pipelineService.recordError(event.runId(), PipelineStage.STORE, event.sourceName(), event.topicId(),
                    "Document without URL, title or topic");
            pipelineService.increment(event.runId(), RunCounter.FAILED);
            return;
        }
        Outcome outcome = process(event);
        pipelineService.increment(event.runId(), RunCounter.PROCESSED);
        pipelineService.increment(event.runId(), switch (outcome) {
            case PUBLISHED, UPDATE -> RunCounter.PUBLISHED;
            case DUPLICATE -> RunCounter.DUPLICATE;
            case REJECTED -> RunCounter.REJECTED;
        });
    }

    Outcome process(ContentCollectedEvent event) {
        double minimumRelevance = event.minimumRelevance() == null ? DEFAULT_MINIMUM_RELEVANCE : event.minimumRelevance();
        String fullText = event.text() == null ? "" : event.text();
        String text = truncate(fullText, MAXIMUM_TEXT_CHARS);
        Instant since = Instant.now().minus(properties.deduplication().window());
        DocumentFingerprint fingerprint = DocumentFingerprint.of(event.url(), event.title(), text);

        // Niveles 1 y 2 — exacto y textual: no cuestan IA.
        Optional<UUID> copy = findKnownCopy(fingerprint, since);
        if (copy.isPresent()) {
            link(copy.get(), event.topicId(), minimumRelevance);
            return Outcome.DUPLICATE;
        }

        Analysis analysis = analyze(event, text);
        Content.Builder draft = draft(event, fingerprint, fullText, text, analysis);
        Optional<String> rejection = rejectionReason(analysis, minimumRelevance);
        if (rejection.isPresent()) {
            return save(draft.decision(ContentStatus.REJECTED, ContentRelation.NEW, null, null, rejection.get()),
                    event.topicId(), analysis.relevanceScore(), Outcome.REJECTED, false);
        }
        // Nivel 3 — semántico: la misma información contada de otra forma.
        return classifyAgainstPublished(event, draft, analysis, since);
    }

    /** La misma URL o el mismo texto (exacto), o casi el mismo texto (SimHash). */
    private Optional<UUID> findKnownCopy(DocumentFingerprint fingerprint, Instant since) {
        Optional<UUID> sameUrl = contentRepository.findByUrlHash(fingerprint.urlHash()).map(Content::getId);
        if (sameUrl.isPresent()) {
            return sameUrl;
        }
        Optional<UUID> sameText = contentRepository
                .findFirstByContentHashAndCollectedAtAfterOrderByCollectedAtAsc(fingerprint.contentHash(), since)
                .map(Content::getId);
        if (sameText.isPresent() || !fingerprint.simhashReliable()) {
            return sameText;
        }
        return contentRepository.findNearTextualDuplicate(fingerprint.simhash(),
                properties.deduplication().simhashMaximumDistance(), since);
    }

    private Analysis analyze(ContentCollectedEvent event, String text) {
        return aiServiceClient.analyze(new AiServiceClient.AnalysisRequest(
                new AiServiceClient.TopicContext(event.topicName(), event.topicDescription(),
                        event.topicKeywords() == null ? List.of() : event.topicKeywords()),
                event.title(), text, event.url(), event.publishedAt(), event.language()));
    }

    private static Content.Builder draft(ContentCollectedEvent event, DocumentFingerprint fingerprint, String fullText,
                                         String text, Analysis analysis) {
        return Content.builder()
                .origin(event.url(), fingerprint.urlHash(), fingerprint.contentHash(), fingerprint.simhash(),
                        event.runId())
                .source(event.sourceId(), event.sourceName(), event.author(), event.imageUrl(), event.publishedAt())
                .text(event.title(), firstNonBlank(analysis.title(), event.title()),
                        firstNonBlank(analysis.summary(), excerpt(text, event.title())), excerpt(text, null),
                        firstNonBlank(analysis.language(), event.language()))
                .body(fullText, MAXIMUM_BODY_CHARS)
                .english(analysis.titleEn(), analysis.summaryEn())
                .analysis(analysis.keywords(), analysis.entities(), analysis.qualityScore(),
                        AiMode.from(analysis.mode()), analysis.embedding());
    }

    /** Por qué no se publica, si no se publica. Con mínimo 0 (países y medios) no se filtra por tema. */
    private Optional<String> rejectionReason(Analysis analysis, double minimumRelevance) {
        if (minimumRelevance > 0 && (!analysis.relevant() || analysis.relevanceScore() < minimumRelevance)) {
            return Optional.of("Not relevant for the topic (%.2f): %s".formatted(analysis.relevanceScore(),
                    analysis.reason() == null ? "" : analysis.reason()));
        }
        if (analysis.qualityScore() < properties.deduplication().minimumQuality()) {
            return Optional.of("Insufficient quality (%.2f)".formatted(analysis.qualityScore()));
        }
        return Optional.empty();
    }

    /** Compara con lo publicado más parecido: nuevo, duplicado o (si la IA lo decide) actualización. */
    private Outcome classifyAgainstPublished(ContentCollectedEvent event, Content.Builder draft, Analysis analysis,
                                             Instant since) {
        UUID topicId = event.topicId();
        double relevance = analysis.relevanceScore();
        Optional<Neighbor> neighbor = analysis.embedding() == null ? Optional.empty()
                : contentRepository.findNearestPublished(Vectors.toLiteral(analysis.embedding()), since);
        Double similarity = neighbor.map(Neighbor::getSimilarity).orElse(null);
        ContentRelation relation = switch (policy.decide(similarity)) {
            case NEW -> ContentRelation.NEW;
            case DUPLICATE -> ContentRelation.DUPLICATE;
            case ASK_AI -> askTheAi(neighbor.orElseThrow(), analysis, event.publishedAt());
        };
        return switch (relation) {
            case NEW -> save(draft.decision(ContentStatus.PUBLISHED, ContentRelation.NEW, null, similarity, null),
                    topicId, relevance, Outcome.PUBLISHED, false);
            case UPDATE -> save(draft.decision(ContentStatus.PUBLISHED, ContentRelation.UPDATE,
                    neighbor.orElseThrow().getId(), similarity, null), topicId, relevance, Outcome.UPDATE, false);
            case DUPLICATE -> {
                UUID previousId = neighbor.orElseThrow().getId();
                // El contenido original pasa a estar disponible también para este tópico.
                link(previousId, topicId, relevance);
                yield save(draft.decision(ContentStatus.DUPLICATE, ContentRelation.DUPLICATE, previousId, similarity,
                        null), topicId, relevance, Outcome.DUPLICATE, true);
            }
        };
    }

    private ContentRelation askTheAi(Neighbor previous, Analysis analysis, OffsetDateTime publishedAt) {
        AiServiceClient.Comparison comparison = aiServiceClient.compare(new AiServiceClient.ComparisonRequest(
                new AiServiceClient.ComparedItem(previous.getTitle(), previous.getSummary(),
                        previous.getPublicationDate() == null ? null
                                : previous.getPublicationDate().atOffset(ZoneOffset.UTC)),
                new AiServiceClient.ComparedItem(analysis.title(), analysis.summary(), publishedAt)));
        return comparison == null || comparison.verdict() == null ? ContentRelation.NEW : comparison.verdict();
    }

    private Outcome save(Content.Builder draft, UUID topicId, double relevance, Outcome outcome, boolean skipLink) {
        Content content = draft.build();
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (!skipLink) {
                    content.linkTopic(topicId, relevance);
                }
                contentRepository.save(content);
            });
            log.debug("Stored content {} as {}", content.getUrlHash(), content.getStatus());
            return outcome;
        } catch (DataIntegrityViolationException race) {
            // Otro consumidor guardó la misma URL en paralelo.
            contentRepository.findByUrlHash(content.getUrlHash()).ifPresent(existing -> link(existing.getId(), topicId, relevance));
            return Outcome.DUPLICATE;
        }
    }

    private void link(UUID contentId, UUID topicId, double relevance) {
        transactionTemplate.executeWithoutResult(status -> contentTopicRepository.link(contentId, topicId, relevance));
    }

    private static String excerpt(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        return truncate(text, EXCERPT_CHARS);
    }
}
