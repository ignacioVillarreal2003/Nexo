package com.nexo.content.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.content.application.service.ContentIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentCollectedConsumer {

    private final ContentIngestionService ingestionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${nexo.collected.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "collectedListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) throws JsonProcessingException {
        MessageCorrelation.put(MessageCorrelation.from(record));
        try {
            ContentCollectedEvent event = objectMapper.readValue(record.value(), ContentCollectedEvent.class);
            ingestionService.ingest(event);
        } finally {
            MessageCorrelation.clear();
        }
    }
}
