package com.nexo.delivery.infrastructure.messaging;

import com.nexo.delivery.domain.config.ApplicationProperties;
import com.nexo.delivery.domain.entity.OutboxEvent;
import com.nexo.delivery.infrastructure.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Publica en Kafka las filas pendientes del outbox, con reintentos y un tope de intentos. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private static final int SEND_TIMEOUT_SECONDS = 10;
    private static final int PUBLISHED_RETENTION_DAYS = 7;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ApplicationProperties properties;

    @Scheduled(fixedDelayString = "${nexo.events.relay-interval}")
    @SchedulerLock(name = "outbox-relay", lockAtMostFor = "PT1M")
    @Transactional
    public void relayPendingEvents() {
        ApplicationProperties.Events events = properties.events();
        List<OutboxEvent> batch = outboxEventRepository.findNextBatch(
                events.relayMaxAttempts(), PageRequest.of(0, events.relayBatchSize()));
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(MessageCorrelation.stamp(
                                new ProducerRecord<>(event.getTopic(), event.getAggregateId(), event.getPayload()),
                                event.getCorrelationId()))
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.markFailed(e.toString());
                log.error("Outbox relay interrupted while publishing event {}", event.getId(), e);
                return;
            } catch (Exception e) {
                event.markFailed(e.toString());
                if (event.getAttempts() >= events.relayMaxAttempts()) {
                    event.discard(e.toString());
                    log.error("Discarding outbox event {} after {} attempts", event.getId(), event.getAttempts(), e);
                } else {
                    log.error("Failed to publish outbox event {} (attempt {})", event.getId(), event.getAttempts(), e);
                }
            }
        }
    }

    @Scheduled(cron = "0 30 3 * * *")
    @SchedulerLock(name = "outbox-cleanup", lockAtMostFor = "PT10M")
    @Transactional
    public void purgePublishedEvents() {
        Instant threshold = Instant.now().minus(PUBLISHED_RETENTION_DAYS, ChronoUnit.DAYS);
        long removed = outboxEventRepository.deleteByPublishedAtBefore(threshold);
        if (removed > 0) {
            log.info("Purged {} published outbox events older than {} days", removed, PUBLISHED_RETENTION_DAYS);
        }
    }
}
