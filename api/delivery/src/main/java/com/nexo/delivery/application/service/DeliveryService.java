package com.nexo.delivery.application.service;

import com.nexo.delivery.domain.config.ApplicationProperties;
import com.nexo.delivery.domain.entity.Delivery;
import com.nexo.delivery.domain.entity.DeliveryBatch;
import com.nexo.delivery.domain.entity.DeliveryRun;
import com.nexo.delivery.domain.entity.enums.DeliveryRelation;
import com.nexo.delivery.domain.entity.enums.RunTrigger;
import com.nexo.delivery.domain.selection.SelectionPolicy;
import com.nexo.delivery.domain.util.Vectors;
import com.nexo.delivery.infrastructure.client.ContentServiceClient;
import com.nexo.delivery.infrastructure.client.ContentServiceClient.Candidate;
import com.nexo.delivery.infrastructure.client.SubscriptionServiceClient;
import com.nexo.delivery.infrastructure.client.SubscriptionServiceClient.DeliveryPlan;
import com.nexo.delivery.infrastructure.client.TopicServiceClient;
import com.nexo.delivery.infrastructure.repository.DeliveryBatchRepository;
import com.nexo.delivery.infrastructure.repository.DeliveryRepository;
import com.nexo.delivery.infrastructure.repository.DeliveryRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Arma la entrega diaria de cada usuario: para cada tópico suscripto elige el mejor contenido publicado
 * que todavía no recibió, aplica la antirrepetición por usuario y los límites diarios, y encola el aviso.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeliveryService {

    /** {@code topicsWithoutContent}: tópicos del usuario para los que no había ningún candidato publicado. */
    public record UserResult(UUID userId, LocalDate date, int delivered, List<UUID> topicsWithoutContent) {

        public UserResult(UUID userId, LocalDate date, int delivered) {
            this(userId, date, delivered, List.of());
        }
    }

    public record RunResult(UUID runId, int users, int deliveries, int errors) {
    }

    /** Resultado de "mandarme por mail": cuántos contenidos salieron y si hay tópicos recopilándose. */
    public record EmailResult(int emailed, List<UUID> topicsWithoutContent) {
    }

    private record DeliveredItem(UUID deliveryId, UUID contentId) {
    }

    /** Lo elegido de un tópico. {@code hadCandidates} en false: el tópico todavía no tiene contenido publicado. */
    private record TopicResult(boolean hadCandidates, List<DeliveredItem> items) {

        static final TopicResult NO_CANDIDATES = new TopicResult(false, List.of());
    }

    private record Choice(Candidate candidate, boolean update) {
    }

    private static final int MINIMUM_CANDIDATES = 20;

    private final SubscriptionServiceClient subscriptionClient;
    private final TopicServiceClient topicClient;
    private final ContentServiceClient contentClient;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryBatchRepository batchRepository;
    private final DeliveryRunRepository runRepository;
    private final DeliveryBriefService briefService;
    private final DigestDispatchService digestDispatch;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationProperties properties;
    private final SelectionPolicy policy;
    private final ExecutorService deliveryWorkers;

    /** Cada hora: entrega a los usuarios cuya hora local de entrega es la actual. */
    @Scheduled(cron = "${nexo.selection.schedule}", zone = "UTC")
    @SchedulerLock(name = "delivery-hourly", lockAtMostFor = "PT50M")
    public void scheduledRun() {
        try {
            List<UUID> due = subscriptionClient.usersDueAt(Instant.now());
            if (!due.isEmpty()) {
                run(due, RunTrigger.SCHEDULED);
            }
        } catch (RuntimeException e) {
            log.error("The scheduled delivery failed: {}", e.getMessage(), e);
        }
    }

    /** Entrega manual desde la administración: para un usuario o para todos los que tienen suscripciones. */
    public RunResult runManually(UUID userId) {
        List<UUID> users = userId != null ? List.of(userId) : subscriptionClient.subscribers();
        return run(users, RunTrigger.MANUAL);
    }

    private RunResult run(List<UUID> userIds, RunTrigger trigger) {
        DeliveryRun run = runRepository.save(DeliveryRun.start(trigger));
        List<Future<UserResult>> futures = userIds.stream().map(id -> deliveryWorkers.submit(() -> deliverFor(id))).toList();
        int deliveries = 0;
        int errors = 0;
        List<String> notes = new ArrayList<>();
        for (Future<UserResult> future : futures) {
            try {
                deliveries += future.get().delivered();
            } catch (Exception e) {
                errors++;
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (notes.size() < 5) {
                    notes.add(cause.getMessage());
                }
                log.warn("Delivery to a user failed: {}", cause.getMessage());
            }
        }
        run.finish(userIds.size(), deliveries, errors, notes.isEmpty() ? null : String.join(" · ", notes));
        runRepository.save(run);
        log.info("{} delivery finished: {} users, {} items, {} errors", trigger, userIds.size(), deliveries, errors);
        return new RunResult(run.getId(), userIds.size(), deliveries, errors);
    }

    /**
     * Idempotente: los tópicos ya entregados hoy se saltan, así que puede invocarse varias veces en el día
     * (por ejemplo, cuando el usuario sigue un tópico nuevo y pide su contenido de inmediato).
     */
    public UserResult deliverFor(UUID userId) {
        return deliverFor(userId, true);
    }

    /** @param sendDigest {@code false} cuando quien llama manda su propio correo con todo el día (envío manual). */
    private UserResult deliverFor(UUID userId, boolean sendDigest) {
        DeliveryPlan plan = subscriptionClient.plan(userId);
        LocalDate today = LocalDate.now(zone(plan == null ? null : plan.timezone()));
        if (plan == null || plan.topics() == null || plan.topics().isEmpty()) {
            return new UserResult(userId, today, 0);
        }
        int remaining = plan.maximumDailyItems() - (int) deliveryRepository.countByUserIdAndDeliveryDate(userId, today);
        Map<UUID, TopicServiceClient.TopicReference> topics = topicClient.references(
                plan.topics().stream().map(SubscriptionServiceClient.PlannedTopic::topicId).toList());
        Instant historySince = Instant.now().minus(properties.selection().historyWindow());

        List<DeliveredItem> delivered = new ArrayList<>();
        List<UUID> withoutContent = new ArrayList<>();
        for (SubscriptionServiceClient.PlannedTopic planned : plan.topics()) {
            if (remaining <= 0) {
                break;
            }
            if (batchRepository.existsById(new DeliveryBatch.Id(userId, planned.topicId(), today))) {
                continue; // ya entregado hoy
            }
            TopicServiceClient.TopicReference topic = topics.get(planned.topicId());
            TopicResult result = deliverTopic(userId, planned, Math.min(planned.dailyLimit(), remaining), today,
                    topic == null ? null : topic.name(), historySince);
            if (!result.hadCandidates()) {
                withoutContent.add(planned.topicId());
            }
            delivered.addAll(result.items());
            remaining -= result.items().size();
        }

        if (!delivered.isEmpty()) {
            // Ya elegidas: la IA lee cada nota entera y escribe su informe completo (en segundo plano).
            briefService.request(delivered.stream().map(DeliveredItem::contentId).distinct().toList());
            if (sendDigest) {
                digestDispatch.queue(userId, today, false);
            }
        }
        log.debug("Delivered {} items to {}", delivered.size(), userId);
        return new UserResult(userId, today, delivered.size(), List.copyOf(withoutContent));
    }

    /** Elige lo nuevo de un tópico para el usuario y lo guarda (con el lote del día) en una transacción. */
    private TopicResult deliverTopic(UUID userId, SubscriptionServiceClient.PlannedTopic planned, int limit,
                                     LocalDate today, String topicName, Instant historySince) {
        List<Candidate> candidates = contentClient.candidates(planned.topicId(),
                Math.max(limit * properties.selection().candidateFactor(), MINIMUM_CANDIDATES));
        if (candidates.isEmpty()) {
            return TopicResult.NO_CANDIDATES;
        }
        List<Choice> choices = choose(userId, candidates, limit, historySince);
        if (choices.isEmpty()) {
            return new TopicResult(true, List.of()); // sin novedades por ahora: se reintenta en la próxima ejecución
        }
        List<DeliveredItem> items = transactionTemplate.execute(status ->
                save(userId, planned.topicId(), topicName, today, choices));
        return new TopicResult(true, items == null ? List.of() : items);
    }

    /** Antirrepetición por usuario: fuera lo que ya recibió y lo demasiado parecido a su historial. */
    private List<Choice> choose(UUID userId, List<Candidate> candidates, int limit, Instant historySince) {
        Set<UUID> alreadyDelivered = new HashSet<>(deliveryRepository.findDeliveredContentIds(userId,
                candidates.stream().map(Candidate::id).toList()));
        Map<UUID, Candidate> byId = new HashMap<>();
        List<SelectionPolicy.Candidate> pool = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (!alreadyDelivered.contains(candidate.id())) {
                byId.put(candidate.id(), candidate);
                pool.add(new SelectionPolicy.Candidate(candidate.id(), candidate.isUpdate(),
                        candidate.relatedContentId(), candidate.embedding()));
            }
        }
        return policy.select(pool, limit,
                        embedding -> deliveryRepository.findMaximumSimilarity(userId, Vectors.toLiteral(embedding),
                                historySince).orElse(null),
                        contentId -> deliveryRepository.existsByUserIdAndContentId(userId, contentId))
                .stream()
                .map(selected -> new Choice(byId.get(selected.candidate().id()), selected.update()))
                .toList();
    }

    private List<DeliveredItem> save(UUID userId, UUID topicId, String topicName, LocalDate today,
                                     List<Choice> choices) {
        List<DeliveredItem> items = new ArrayList<>();
        for (Choice choice : choices) {
            Candidate c = choice.candidate();
            Delivery saved = deliveryRepository.save(Delivery.of(userId, topicId, topicName, today,
                    c.id(), c.title(), c.summary(), c.originalUrl(), c.sourceName(), c.imageUrl(),
                    c.publicationDate(), c.collectedAt(),
                    choice.update() ? DeliveryRelation.UPDATE : DeliveryRelation.NEW,
                    c.relatedContentId(), c.embedding()).withEnglish(c.titleEn(), c.summaryEn()));
            items.add(new DeliveredItem(saved.getId(), c.id()));
        }
        batchRepository.save(DeliveryBatch.of(userId, topicId, today, items.size()));
        return items;
    }

    /**
     * "Enviarme por mail" (manual): completa el día con lo nuevo que haya y encola un correo con <b>todo el día</b>,
     * cada vez que se pide. El correo sale cuando las noticias tienen su informe completo (en general, en uno o dos
     * minutos). El resumen automático diario, en cambio, nunca repite contenidos ({@code emailed_at}).
     */
    public EmailResult emailDay(UUID userId) {
        UserResult fresh = deliverFor(userId, false);
        List<Delivery> day = deliveryRepository.findDay(userId, fresh.date(), null);
        if (day.isEmpty()) {
            return new EmailResult(0, fresh.topicsWithoutContent());
        }
        briefService.request(day.stream().filter(d -> !d.isBriefReady()).map(Delivery::getContentId).distinct().toList());
        digestDispatch.queue(userId, fresh.date(), true);
        return new EmailResult(Math.min(day.size(), properties.digest().onDemandItems()),
                fresh.topicsWithoutContent());
    }

    public LocalDate todayFor(UUID userId) {
        try {
            DeliveryPlan plan = subscriptionClient.plan(userId);
            return LocalDate.now(zone(plan == null ? null : plan.timezone()));
        } catch (RuntimeException e) {
            return LocalDate.now(ZoneOffset.UTC);
        }
    }

    private static ZoneId zone(String timezone) {
        try {
            return timezone == null ? ZoneOffset.UTC : ZoneId.of(timezone);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
