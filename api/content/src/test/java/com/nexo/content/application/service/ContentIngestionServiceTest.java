package com.nexo.content.application.service;

import com.nexo.content.application.service.ContentIngestionService.Outcome;
import com.nexo.content.domain.config.ApplicationProperties;
import com.nexo.content.domain.entity.Content;
import com.nexo.content.domain.entity.enums.ContentRelation;
import com.nexo.content.domain.entity.enums.ContentStatus;
import com.nexo.content.infrastructure.client.AiServiceClient;
import com.nexo.content.infrastructure.client.AiServiceClient.Analysis;
import com.nexo.content.infrastructure.messaging.ContentCollectedEvent;
import com.nexo.content.infrastructure.repository.ContentRepository;
import com.nexo.content.infrastructure.repository.ContentRepository.Neighbor;
import com.nexo.content.infrastructure.repository.ContentTopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContentIngestionServiceTest {

    private static final UUID TOPIC = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final float[] EMBEDDING = {0.1f, 0.2f, 0.3f};
    private static final String TEXT = "El gobierno anunció un plan de inversión en la red eléctrica nacional con nuevas "
            + "obras de transmisión, parques solares y almacenamiento que se licitarán durante el próximo año.";

    private final ContentRepository contents = mock(ContentRepository.class);
    private final ContentTopicRepository topics = mock(ContentTopicRepository.class);
    private final AiServiceClient ai = mock(AiServiceClient.class);
    private final PipelineService pipeline = mock(PipelineService.class);
    private final ApplicationProperties properties = mock(ApplicationProperties.class);
    private ContentIngestionService service;

    @BeforeEach
    void setUp() {
        when(properties.deduplication()).thenReturn(
                new ApplicationProperties.Deduplication(0.92, 0.80, 3, Duration.ofDays(14), 0.35));
        when(contents.findByUrlHash(anyString())).thenReturn(Optional.empty());
        when(contents.findFirstByContentHashAndCollectedAtAfterOrderByCollectedAtAsc(anyString(), any()))
                .thenReturn(Optional.empty());
        when(contents.findNearTextualDuplicate(anyLong(), anyInt(), any())).thenReturn(Optional.empty());
        when(contents.findNearestPublished(anyString(), any())).thenReturn(Optional.empty());
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ContentIngestionService(contents, topics, ai, pipeline, properties,
                new TransactionTemplate(transactions));
    }

    private static ContentCollectedEvent event(Double minimumRelevance) {
        return new ContentCollectedEvent(UUID.randomUUID(), TOPIC, "Energía", null, List.of("energía"),
                minimumRelevance, UUID.randomUUID(), "El Medio", "https://medio.uy/nota?utm_source=x",
                "Plan eléctrico", TEXT, null, null, null, "es");
    }

    private static Analysis analysis(boolean relevant, double relevance, double quality) {
        return new Analysis(relevant, relevance, quality, "Plan eléctrico nacional", "Resumen.", "Power plan",
                "Summary.", List.of("energía"), List.of(), "es", "GEMINI", "central", EMBEDDING);
    }

    private Content saved() {
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contents).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void theSameUrlIsADuplicateWithoutAskingTheAi() {
        Content existing = mock(Content.class);
        UUID existingId = UUID.randomUUID();
        when(existing.getId()).thenReturn(existingId);
        when(contents.findByUrlHash(anyString())).thenReturn(Optional.of(existing));

        assertThat(service.process(event(null))).isEqualTo(Outcome.DUPLICATE);

        verify(topics).link(eq(existingId), eq(TOPIC), anyDouble());
        verify(ai, never()).analyze(any());
        verify(contents, never()).save(any());
    }

    @Test
    void contentThatIsNotAboutTheTopicIsRejected() {
        when(ai.analyze(any())).thenReturn(analysis(false, 0.2, 0.9));

        assertThat(service.process(event(0.55))).isEqualTo(Outcome.REJECTED);

        Content content = saved();
        assertThat(content.getStatus()).isEqualTo(ContentStatus.REJECTED);
        assertThat(content.getRejectionReason()).startsWith("Not relevant for the topic");
    }

    @Test
    void withMinimumRelevanceZeroEverythingRelevantEnoughInQualityIsPublished() {
        when(ai.analyze(any())).thenReturn(analysis(false, 0.1, 0.9));

        assertThat(service.process(event(0.0))).isEqualTo(Outcome.PUBLISHED);
        assertThat(saved().getStatus()).isEqualTo(ContentStatus.PUBLISHED);
    }

    @Test
    void aSimilarNeighborBecomesAnUpdateWhenTheAiSaysSo() {
        when(ai.analyze(any())).thenReturn(analysis(true, 0.9, 0.9));
        Neighbor neighbor = mock(Neighbor.class);
        UUID previous = UUID.randomUUID();
        when(neighbor.getId()).thenReturn(previous);
        when(neighbor.getTitle()).thenReturn("Plan eléctrico");
        when(neighbor.getSummary()).thenReturn("Anterior.");
        when(neighbor.getPublicationDate()).thenReturn(Instant.now());
        when(neighbor.getSimilarity()).thenReturn(0.85); // entre 0,80 y 0,92: decide la IA
        when(contents.findNearestPublished(anyString(), any())).thenReturn(Optional.of(neighbor));
        when(ai.compare(any())).thenReturn(new AiServiceClient.Comparison(ContentRelation.UPDATE, "nuevos datos", "GEMINI"));

        assertThat(service.process(event(0.55))).isEqualTo(Outcome.UPDATE);

        Content content = saved();
        assertThat(content.getRelation()).isEqualTo(ContentRelation.UPDATE);
        assertThat(content.getRelatedContentId()).isEqualTo(previous);
    }

    @Test
    void aVeryCloseNeighborIsADuplicateAndTheOriginalGetsTheTopic() {
        when(ai.analyze(any())).thenReturn(analysis(true, 0.9, 0.9));
        Neighbor neighbor = mock(Neighbor.class);
        UUID previous = UUID.randomUUID();
        when(neighbor.getId()).thenReturn(previous);
        when(neighbor.getSimilarity()).thenReturn(0.97);
        when(contents.findNearestPublished(anyString(), any())).thenReturn(Optional.of(neighbor));

        assertThat(service.process(event(0.55))).isEqualTo(Outcome.DUPLICATE);

        verify(topics).link(eq(previous), eq(TOPIC), anyDouble());
        verify(ai, never()).compare(any());
        assertThat(saved().getStatus()).isEqualTo(ContentStatus.DUPLICATE);
    }
}
