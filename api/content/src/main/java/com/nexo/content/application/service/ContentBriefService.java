package com.nexo.content.application.service;

import com.nexo.content.domain.dto.response.ContentBriefResponse;
import com.nexo.content.domain.config.ApplicationProperties;
import com.nexo.content.domain.entity.Content;
import com.nexo.content.domain.entity.enums.AiMode;
import com.nexo.content.domain.entity.enums.BriefStatus;
import com.nexo.content.infrastructure.client.AiServiceClient;
import com.nexo.content.infrastructure.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Informe completo de las noticias elegidas para los lectores.
 *
 * <p>El análisis al recopilar es corto y a veces se basa solo en el copete del feed. Cuando delivery elige una noticia
 * para alguien, pide su informe: la IA entra a la nota, la lee entera y escribe un resumen completo en párrafos, los
 * puntos clave y por qué importa, en español y en inglés. Se genera una sola vez por contenido y queda guardado.
 *
 * <p>Va en segundo plano y de a pocos para cuidar la cuota de la IA. Mientras haya modelo disponible se exige que lo
 * escriba la IA; si un informe lleva mucho esperando (sin cuota), se acepta el respaldo extractivo para no dejar al
 * lector sin nada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentBriefService {

    private final ContentRepository contentRepository;
    private final AiServiceClient aiServiceClient;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationProperties properties;

    /** Encola el informe de los contenidos que no lo tienen y devuelve los que ya están listos. */
    public List<ContentBriefResponse> request(Collection<UUID> ids) {
        List<ContentBriefResponse> ready = transactionTemplate.execute(status -> {
            List<Content> contents = contentRepository.findAllById(ids);
            long queued = contents.stream().filter(Content::requestBrief).count();
            if (queued > 0) {
                log.info("Queued the full brief of {} contents", queued);
            }
            return contents.stream().filter(Content::hasBrief).map(ContentBriefService::toResponse).toList();
        });
        return ready == null ? List.of() : ready;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT20S")
    @SchedulerLock(name = "content-brief-writer", lockAtMostFor = "PT15M")
    public void writePending() {
        List<UUID> pending = contentRepository.findIdsByBriefStatus(BriefStatus.PENDING,
                PageRequest.of(0, properties.briefs().perRun()));
        int written = 0;
        for (UUID id : pending) {
            if (!write(id)) {
                break; // la IA no está disponible ahora: el resto espera a la próxima pasada
            }
            written++;
        }
        if (written > 0) {
            log.info("Wrote the full brief of {} contents", written);
        }
    }

    /** @return {@code false} si la IA no pudo ahora (sin cuota o caída). */
    boolean write(UUID id) {
        Content content = contentRepository.findById(id).orElse(null);
        if (content == null || !content.isBriefPending()) {
            return true;
        }
        boolean allowFallback = content.getBriefRequestedAt() != null
                && content.getBriefRequestedAt().isBefore(Instant.now().minus(properties.briefs().fallbackAfter()));
        int maximumAttempts = properties.briefs().maximumAttempts();
        String text = content.getBodyText() != null ? content.getBodyText() : content.getBodyExcerpt();
        AiServiceClient.BriefResult result;
        try {
            result = aiServiceClient.brief(new AiServiceClient.BriefRequest(content.getOriginalTitle(),
                    text == null ? "" : text, content.getOriginalUrl(), content.getSourceName(),
                    null, content.getPublicationDate() == null ? null
                    : content.getPublicationDate().withOffsetSameInstant(ZoneOffset.UTC), allowFallback));
        } catch (RuntimeException e) {
            log.info("Brief of {} postponed, the AI is not available: {}", id, e.getMessage());
            transactionTemplate.executeWithoutResult(status -> contentRepository.findById(id)
                    .ifPresent(c -> c.briefAttemptFailed(allowFallback ? maximumAttempts : Integer.MAX_VALUE)));
            return false;
        }
        if (result.detailedSummary() == null || result.detailedSummary().isBlank()) {
            transactionTemplate.executeWithoutResult(status -> contentRepository.findById(id)
                    .ifPresent(c -> c.briefAttemptFailed(maximumAttempts)));
            return true;
        }
        transactionTemplate.executeWithoutResult(status -> contentRepository.findById(id).ifPresent(c ->
                c.applyBrief(new Content.Brief(result.title(), result.titleEn(), result.summary(), result.summaryEn(),
                        result.detailedSummary(), result.detailedSummaryEn(), result.keyPoints(), result.keyPointsEn(),
                        result.whyItMatters(), result.whyItMattersEn(), result.readingMinutes(), AiMode.from(result.mode()),
                        result.imageUrl(), result.resolvedUrl()))));
        return true;
    }

    static ContentBriefResponse toResponse(Content c) {
        return new ContentBriefResponse(c.getId(), c.getTitle(), c.getSummary(), c.getTitleEn(), c.getSummaryEn(),
                c.getDetailedSummary(), c.getDetailedSummaryEn(), List.copyOf(c.getKeyPoints()),
                List.copyOf(c.getKeyPointsEn()), c.getWhyItMatters(), c.getWhyItMattersEn(), c.getReadingMinutes(),
                c.getImageUrl(), c.getOriginalUrl());
    }
}
