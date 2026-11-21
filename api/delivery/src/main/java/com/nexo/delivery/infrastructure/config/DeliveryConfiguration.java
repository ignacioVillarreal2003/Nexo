package com.nexo.delivery.infrastructure.config;

import com.nexo.delivery.domain.config.ApplicationProperties;
import com.nexo.delivery.domain.selection.SelectionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class DeliveryConfiguration {

    @Bean
    public SelectionPolicy selectionPolicy(ApplicationProperties properties) {
        return new SelectionPolicy(properties.selection().userSimilarityThreshold());
    }

    /** Arma las entregas de varios usuarios en paralelo; Spring lo cierra al apagar la aplicación. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService deliveryWorkers(ApplicationProperties properties) {
        return Executors.newFixedThreadPool(properties.selection().workers());
    }
}
