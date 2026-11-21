package com.nexo.content.application.service;

import com.nexo.content.domain.dto.response.ContentPreviewResponse;
import com.nexo.content.domain.dto.response.TopicContentSummaryResponse;
import com.nexo.content.infrastructure.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentPreviewService {

    private static final int SUMMARY_PREVIEW_CHARS = 220;

    private final ContentRepository contentRepository;

    @Transactional(readOnly = true)
    public TopicContentSummaryResponse forTopic(UUID topicId, int limit) {
        long lastWeek = contentRepository.countPublishedForTopicSince(topicId, Instant.now().minus(Duration.ofDays(7)));
        var latest = contentRepository.findLatestPublished(topicId, limit).stream()
                .map(r -> new ContentPreviewResponse(r.getId(), r.getTitle(), shorten(r.getSummary()), r.getSourceName(),
                        r.getOriginalUrl(), r.getImageUrl(), r.getPublishedAt()))
                .toList();
        return new TopicContentSummaryResponse(lastWeek, latest);
    }

    private static String shorten(String text) {
        if (text == null || text.length() <= SUMMARY_PREVIEW_CHARS) {
            return text;
        }
        int cut = text.lastIndexOf(' ', SUMMARY_PREVIEW_CHARS);
        return text.substring(0, cut > 0 ? cut : SUMMARY_PREVIEW_CHARS) + "…";
    }
}
