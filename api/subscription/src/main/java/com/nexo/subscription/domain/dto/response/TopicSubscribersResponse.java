package com.nexo.subscription.domain.dto.response;

import java.util.UUID;

public record TopicSubscribersResponse(UUID topicId, String topicName, long subscribers) {
}
