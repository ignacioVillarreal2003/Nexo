package com.nexo.content.infrastructure.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nexo.content.application.service.PipelineService;
import com.nexo.content.domain.config.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<PipelineService> pipelineService;

    @Bean
    public NewTopic contentCollectedTopic() {
        return TopicBuilder.name(properties.collected().topic())
                .partitions(properties.collected().partitions())
                .replicas(properties.collected().replicas())
                .build();
    }

    @Bean
    public NewTopic contentCollectedDeadLetterTopic() {
        return TopicBuilder.name(properties.collected().deadLetterTopic())
                .partitions(1)
                .replicas(properties.collected().replicas())
                .build();
    }

    /**
     * Consumidor de documentos recopilados. Los fallos transitorios (IA caída) se reintentan con
     * backoff exponencial; al agotarse, el documento va al dead letter topic y queda registrado
     * como error de la ejecución.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> collectedListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, KafkaTemplate<String, String> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(properties.collected().concurrency());

        String deadLetterTopic = properties.collected().deadLetterTopic();
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(deadLetterTopic, -1));
        ExponentialBackOff backOff = new ExponentialBackOff(2000L, 2.0);
        backOff.setMaxInterval(30000L);
        backOff.setMaxElapsedTime(120000L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            log.error("Routing {}-{}@{} to the dead letter topic {}",
                    record.topic(), record.partition(), record.offset(), deadLetterTopic, exception);
            pipelineService.getObject().recordDeadLetter(String.valueOf(record.value()), exception);
            recoverer.accept(record, exception);
        }, backOff);
        errorHandler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
