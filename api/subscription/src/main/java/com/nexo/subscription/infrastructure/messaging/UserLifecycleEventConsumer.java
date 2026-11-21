package com.nexo.subscription.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.subscription.application.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Borra las suscripciones y la configuración de entrega de las cuentas eliminadas. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserLifecycleEventConsumer {

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${nexo.user-events.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "userEventsListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        MessageCorrelation.put(MessageCorrelation.from(record));
        try {
            UserLifecycleEvent event;
            try {
                event = objectMapper.readValue(record.value(), UserLifecycleEvent.class);
            } catch (JsonProcessingException e) {
                log.error("Skipping an unparseable user lifecycle event ({} bytes)", record.value().length(), e);
                return;
            }
            if (event.eventType() == UserEventType.USER_DELETED && event.userId() != null) {
                subscriptionService.deleteAllFor(event.userId());
            }
        } finally {
            MessageCorrelation.clear();
        }
    }
}
