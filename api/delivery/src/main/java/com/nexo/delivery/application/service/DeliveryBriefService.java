package com.nexo.delivery.application.service;

import com.nexo.delivery.domain.config.ApplicationProperties;
import com.nexo.delivery.domain.entity.Delivery;
import com.nexo.delivery.infrastructure.client.ContentServiceClient;
import com.nexo.delivery.infrastructure.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

/**
 * Informe completo de lo entregado: al elegir noticias para alguien se le pide a content (que hace que la IA lea cada
 * nota entera y la resuma) y, cuando está listo, se copia en cada entrega para "Mi día", el historial y el correo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryBriefService {

    private final DeliveryRepository deliveryRepository;
    private final ContentServiceClient contentClient;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationProperties properties;

    /**
     * Pide los informes que falten y copia los que ya están. Nunca falla: si content no responde, se reintenta en la
     * próxima sincronización. @return cuántas entregas quedaron con su informe.
     */
    public int request(Collection<UUID> contentIds) {
        if (contentIds.isEmpty()) {
            return 0;
        }
        List<ContentServiceClient.ContentBrief> briefs;
        try {
            briefs = contentClient.briefs(List.copyOf(contentIds));
        } catch (RuntimeException e) {
            log.info("Full briefs not requested now, content service unavailable: {}", e.getMessage());
            return 0;
        }
        if (briefs.isEmpty()) {
            return 0;
        }
        Map<UUID, ContentServiceClient.ContentBrief> byContent = briefs.stream()
                .collect(Collectors.toMap(ContentServiceClient.ContentBrief::id, Function.identity(), (a, b) -> a));
        Integer applied = transactionTemplate.execute(status -> {
            List<Delivery> pending = deliveryRepository.findByContentIdInAndBriefReadyFalse(byContent.keySet());
            for (Delivery delivery : pending) {
                ContentServiceClient.ContentBrief b = byContent.get(delivery.getContentId());
                delivery.applyBrief(b.title(), b.summary(), b.titleEn(), b.summaryEn(), b.detailedSummary(),
                        b.detailedSummaryEn(), b.keyPoints(), b.keyPointsEn(), b.whyItMatters(), b.whyItMattersEn(),
                        b.readingMinutes(), b.imageUrl(), b.originalUrl());
            }
            return pending.size();
        });
        return applied == null ? 0 : applied;
    }

    /** Trae los informes que content terminó de escribir desde la última pasada. */
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    @SchedulerLock(name = "delivery-brief-sync", lockAtMostFor = "PT5M")
    public void syncRecent() {
        List<UUID> contentIds = deliveryRepository.findContentIdsWithoutBrief(
                Instant.now().minus(properties.briefs().window()), PageRequest.of(0, properties.briefs().batchSize()));
        int applied = request(contentIds);
        if (applied > 0) {
            log.info("Copied the full brief into {} delivered items", applied);
        }
    }
}
