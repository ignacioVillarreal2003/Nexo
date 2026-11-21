package com.nexo.delivery.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.delivery.domain.config.ApplicationProperties;
import com.nexo.delivery.domain.entity.Delivery;
import com.nexo.delivery.domain.entity.EmailRequest;
import com.nexo.delivery.domain.entity.enums.DeliveryRelation;
import com.nexo.delivery.domain.entity.enums.EmailRequestStatus;
import com.nexo.delivery.infrastructure.messaging.NotificationCommandPayload;
import com.nexo.delivery.infrastructure.messaging.NotificationCommandPublisher;
import com.nexo.delivery.infrastructure.repository.DeliveryRepository;
import com.nexo.delivery.infrastructure.repository.EmailRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DigestDispatchServiceTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final LocalDate DAY = LocalDate.parse("2026-11-15");
    private static final Duration MAXIMUM_WAIT = Duration.ofMinutes(6);

    private final EmailRequestRepository requests = mock(EmailRequestRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final DeliveryBriefService briefs = mock(DeliveryBriefService.class);
    private final NotificationCommandPublisher publisher = mock(NotificationCommandPublisher.class);
    private DigestDispatchService service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ApplicationProperties properties = mock(ApplicationProperties.class);
        when(properties.digest()).thenReturn(new ApplicationProperties.Digest(10, 20, MAXIMUM_WAIT, 2200, 20));
        service = new DigestDispatchService(requests, deliveries, briefs, publisher,
                new DigestComposer(new ObjectMapper().findAndRegisterModules(), properties),
                new TransactionTemplate(transactions), properties);
    }

    private static Delivery item(String title, boolean withBrief) {
        Delivery delivery = Delivery.of(USER, UUID.randomUUID(), "Energía", DAY, UUID.randomUUID(), title,
                "Resumen corto.", "https://medio.uy/" + title, "El Medio", null, null, null, DeliveryRelation.NEW,
                null, null);
        if (withBrief) {
            delivery.applyBrief(title, "Resumen.", title + " (en)", "Summary.", "Párrafo uno.\n\nPárrafo dos.",
                    "One.\n\nTwo.", List.of("Punto"), List.of("Point"), "Importa.", "It matters.", 3, null, null);
        }
        return delivery;
    }

    private EmailRequest pending(boolean onDemand, Instant requestedAt) {
        EmailRequest request = EmailRequest.of(USER, DAY, onDemand);
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(request, "id", id);
        ReflectionTestUtils.setField(request, "requestedAt", requestedAt);
        when(requests.findById(id)).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    void waitsWhileTheBriefsAreNotReady() {
        EmailRequest request = pending(false, Instant.now());
        when(deliveries.findNotEmailed(USER, DAY)).thenReturn(List.of(item("A", true), item("B", false)));

        service.dispatch(request.getId());

        verify(briefs).request(anyCollection()); // pide el que falta
        verify(publisher, never()).publish(any());
        assertThat(request.getStatus()).isEqualTo(EmailRequestStatus.PENDING);
    }

    @Test
    void sendsWhenEveryBriefIsReadyWithBothLanguages() {
        EmailRequest request = pending(false, Instant.now());
        when(deliveries.findNotEmailed(USER, DAY)).thenReturn(List.of(item("A", true), item("B", true)));

        service.dispatch(request.getId());

        ArgumentCaptor<NotificationCommandPayload> sent = ArgumentCaptor.forClass(NotificationCommandPayload.class);
        verify(publisher).publish(sent.capture());
        String items = sent.getValue().email().variables().get("items");
        assertThat(items).contains("\"es\"").contains("\"en\"").contains("Párrafo uno").contains("It matters.");
        assertThat(sent.getValue().category()).isEqualTo(NotificationCommandPayload.CATEGORY_DAILY_DIGEST);
        assertThat(sent.getValue().inApp()).isNotNull();
        verify(deliveries).markEmailed(anyCollection(), any());
        assertThat(request.getStatus()).isEqualTo(EmailRequestStatus.SENT);
        assertThat(request.getItemCount()).isEqualTo(2);
    }

    @Test
    void sendsAnywayAfterTheMaximumWait() {
        EmailRequest request = pending(true, Instant.now().minus(MAXIMUM_WAIT).minusSeconds(1));
        when(deliveries.findDay(USER, DAY, null)).thenReturn(List.of(item("A", false)));

        service.dispatch(request.getId());

        ArgumentCaptor<NotificationCommandPayload> sent = ArgumentCaptor.forClass(NotificationCommandPayload.class);
        verify(publisher).publish(sent.capture());
        // "Enviarme por mail": categoría propia y sin aviso en la campana
        assertThat(sent.getValue().category()).isEqualTo(NotificationCommandPayload.CATEGORY_ON_DEMAND);
        assertThat(sent.getValue().inApp()).isNull();
        assertThat(request.getStatus()).isEqualTo(EmailRequestStatus.SENT);
    }

    @Test
    void aRequestWithNothingToSendEndsEmpty() {
        EmailRequest request = pending(false, Instant.now());
        when(deliveries.findNotEmailed(USER, DAY)).thenReturn(List.of());

        service.dispatch(request.getId());

        verify(publisher, never()).publish(any());
        assertThat(request.getStatus()).isEqualTo(EmailRequestStatus.EMPTY);
    }

    @Test
    void aPendingRequestCoversANewOneOfTheSameKind() {
        when(requests.existsByUserIdAndDeliveryDateAndOnDemandAndStatus(USER, DAY, true, EmailRequestStatus.PENDING))
                .thenReturn(true);

        service.queue(USER, DAY, true);

        verify(requests, never()).save(any());
    }

    @Test
    void longSummariesAreCutAtAWordAndTextsWithoutSpacesAtTheLimit() {
        assertThat(DigestComposer.shorten("uno dos tres", 7)).isEqualTo("uno dos…");
        assertThat(DigestComposer.shorten("x".repeat(30), 10)).isEqualTo("x".repeat(10) + "…");
        assertThat(DigestComposer.shorten("corto", 10)).isEqualTo("corto");
        assertThat(DigestComposer.outlet("Xataka · vía Google News (ES)")).isEqualTo("Xataka");
    }
}
