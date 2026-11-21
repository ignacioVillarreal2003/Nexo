package com.nexo.delivery.infrastructure.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nexo.delivery.domain.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class KafkaConfiguration {

    private final ApplicationProperties properties;

    private String userEventsDeadLetterTopicName() {
        return properties.userEvents().deadLetterTopic() + ".delivery";
    }

    @Bean
    public NewTopic userEventsDeadLetterTopic() {
        return TopicBuilder.name(userEventsDeadLetterTopicName())
                .partitions(1)
                .replicas(properties.userEvents().replicas())
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> userEventsListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(properties.userEvents().concurrency());
        String deadLetterTopic = userEventsDeadLetterTopicName();
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(deadLetterTopic, -1));
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(10000L);
        backOff.setMaxElapsedTime(60000L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            log.error("Routing {}-{}@{} to the dead letter topic {}",
                    record.topic(), record.partition(), record.offset(), deadLetterTopic, exception);
            recoverer.accept(record, exception);
        }, backOff);
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
