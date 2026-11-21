package com.nexo.delivery.application.service;

import com.nexo.delivery.domain.config.ApplicationProperties;
import com.nexo.delivery.domain.entity.Delivery;
import com.nexo.delivery.domain.entity.EmailRequest;
import com.nexo.delivery.domain.entity.enums.EmailRequestStatus;
import com.nexo.delivery.infrastructure.messaging.NotificationCommandPublisher;
import com.nexo.delivery.infrastructure.repository.DeliveryRepository;
import com.nexo.delivery.infrastructure.repository.EmailRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
/**
 * Manda los correos de resumen. Cada pedido espera a que sus noticias tengan el informe completo (lo escribe la IA
 * leyendo la nota entera) para que el correo traiga la noticia resumida de verdad y no solo el titular; pasado
 * el tiempo máximo de espera ({@code nexo.digest.maximum-wait}) sale igual, con el resumen corto en las que todavía no lo tengan.
 *
 * <p>El correo lleva cada noticia en español y en inglés; notification elige según el idioma de quien lo recibe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DigestDispatchService {

    private final EmailRequestRepository requestRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryBriefService briefService;
    private final NotificationCommandPublisher notificationPublisher;
    private final DigestComposer composer;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationProperties properties;

    /**
     * Encola un correo. Un pedido igual que siga pendiente lo cubre (el resumen automático junta todo lo que no salió
     * por mail al momento de mandarse; el manual, todo el día).
     */
    public void queue(UUID userId, LocalDate date, boolean onDemand) {
        transactionTemplate.executeWithoutResult(status -> {
            if (!requestRepository.existsByUserIdAndDeliveryDateAndOnDemandAndStatus(userId, date, onDemand,
                    EmailRequestStatus.PENDING)) {
                requestRepository.save(EmailRequest.of(userId, date, onDemand));
            }
        });
    }

    @Scheduled(initialDelayString = "PT20S", fixedDelayString = "PT20S")
    @SchedulerLock(name = "delivery-digest-dispatch", lockAtMostFor = "PT5M")
    public void dispatch() {
        List<EmailRequest> pending = requestRepository.findByStatusOrderByRequestedAt(EmailRequestStatus.PENDING,
                PageRequest.of(0, properties.digest().perRun()));
        for (EmailRequest request : pending) {
            try {
                dispatch(request.getId());
            } catch (RuntimeException e) {
                log.warn("Digest email {} not sent yet: {}", request.getId(), e.getMessage());
            }
        }
    }

    void dispatch(UUID requestId) {
        EmailRequest request = requestRepository.findById(requestId).orElse(null);
        if (request == null || !request.isPending()) {
            return;
        }
        List<Delivery> items = items(request);
        if (items.isEmpty()) {
            transactionTemplate.executeWithoutResult(status -> requestRepository.findById(requestId)
                    .ifPresent(EmailRequest::empty));
            return;
        }
        List<UUID> missing = items.stream().filter(d -> !d.isBriefReady()).map(Delivery::getContentId).distinct().toList();
        if (!missing.isEmpty()) {
            briefService.request(missing);
            items = items(request);
        }
        boolean allReady = items.stream().allMatch(Delivery::isBriefReady);
        boolean waitedEnough = request.getRequestedAt().isBefore(Instant.now().minus(properties.digest().maximumWait()));
        if (!allReady && !waitedEnough) {
            return; // se vuelve a mirar en la próxima pasada
        }
        List<Delivery> included = items;
        transactionTemplate.executeWithoutResult(status -> {
            notificationPublisher.publish(composer.compose(request, included));
            deliveryRepository.markEmailed(included.stream().map(Delivery::getId).toList(), Instant.now());
            requestRepository.findById(requestId).ifPresent(r -> r.sent(included.size()));
        });
        log.info("Digest email sent to {}: {} items, {} with their full brief", request.getUserId(), included.size(),
                included.stream().filter(Delivery::isBriefReady).count());
    }

    private List<Delivery> items(EmailRequest request) {
        List<Delivery> day = request.isOnDemand()
                ? deliveryRepository.findDay(request.getUserId(), request.getDeliveryDate(), null)
                : deliveryRepository.findNotEmailed(request.getUserId(), request.getDeliveryDate());
        int limit = request.isOnDemand() ? properties.digest().onDemandItems() : properties.digest().automaticItems();
        return day.stream().limit(limit).toList();
    }
}
