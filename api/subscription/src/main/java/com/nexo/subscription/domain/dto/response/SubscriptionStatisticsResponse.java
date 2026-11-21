package com.nexo.subscription.domain.dto.response;

import java.util.List;

public record SubscriptionStatisticsResponse(long activeSubscriptions, long subscribedUsers,
                                             List<TopicSubscribersResponse> topTopics) {
}
