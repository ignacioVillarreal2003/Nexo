package com.nexo.authentication.infrastructure.messaging;

import com.nexo.authentication.domain.util.RequestContext;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;

public final class MessageCorrelation {

    public static final String HEADER = RequestContext.REQUEST_ID_HEADER;

    private MessageCorrelation() {
    }

    public static String current() {
        return MDC.get(RequestContext.REQUEST_ID_MDC_KEY);
    }

    public static <K, V> ProducerRecord<K, V> stamp(ProducerRecord<K, V> record, String correlationId) {
        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add(HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
