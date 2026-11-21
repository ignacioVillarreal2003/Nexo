package com.nexo.delivery.domain.dto.response;

public record DeliveryStatisticsResponse(long deliveriesLast24Hours, long deliveriesLast7Days, long readLast7Days,
                                         long favorites, long usersServedLast7Days) {
}
