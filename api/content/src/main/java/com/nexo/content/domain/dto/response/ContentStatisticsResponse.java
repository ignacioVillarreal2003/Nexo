package com.nexo.content.domain.dto.response;

import java.util.Map;

public record ContentStatisticsResponse(Map<String, Long> byStatus, long publishedLast24Hours,
                                        long publishedLast7Days) {
}
