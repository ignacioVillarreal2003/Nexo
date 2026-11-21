package com.nexo.delivery.domain.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** "Mi día": los contenidos de una fecha y cuántos hay por tópico. */
public record DayResponse(LocalDate date, LocalDate today, List<TopicCount> topics, List<FeedItemResponse> items) {

    public record TopicCount(UUID topicId, String topicName, long count) {
    }
}
