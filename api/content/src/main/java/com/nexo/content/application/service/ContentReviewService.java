package com.nexo.content.application.service;

import com.nexo.content.application.mapper.ContentMapper;
import com.nexo.content.application.security.AuthenticatedUser;
import com.nexo.content.domain.dto.request.ReviewContentRequest;
import com.nexo.content.domain.dto.response.ContentResponse;
import com.nexo.content.domain.dto.response.ContentStatisticsResponse;
import com.nexo.content.domain.dto.response.PageResponse;
import com.nexo.content.domain.entity.AuditLog;
import com.nexo.content.domain.entity.Content;
import com.nexo.content.domain.entity.enums.ContentStatus;
import com.nexo.content.domain.exception.BadRequestException;
import com.nexo.content.domain.exception.ResourceNotFoundException;
import com.nexo.content.infrastructure.repository.ContentRepository;
import com.nexo.content.infrastructure.repository.specification.ContentSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.nexo.content.domain.util.Texts.trimToNull;

/** Revisión y corrección manual de contenidos desde la administración. */
@Service
@RequiredArgsConstructor
public class ContentReviewService {

    public static final String CONTENT_REVIEWED = "CONTENT_REVIEWED";
    private static final Set<ContentStatus> REVIEWABLE = Set.of(ContentStatus.PUBLISHED, ContentStatus.HIDDEN,
            ContentStatus.REJECTED);

    private final ContentRepository contentRepository;
    private final ContentMapper contentMapper;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<ContentResponse> search(ContentStatus status, UUID topicId, String search, int page, int size) {
        return PageResponse.from(contentRepository.findAll(ContentSpecifications.search(status, topicId, search),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "collectedAt"))),
                contentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ContentResponse get(UUID id) {
        return contentMapper.toResponse(find(id));
    }

    @Transactional
    public ContentResponse review(UUID id, ReviewContentRequest request, AuthenticatedUser actor) {
        if (request.status() != null && !REVIEWABLE.contains(request.status())) {
            throw new BadRequestException("Content can only be published, hidden or rejected manually");
        }
        Content content = find(id);
        content.review(trimToNull(request.title()), trimToNull(request.summary()), request.status());
        auditService.record(actor.id(), actor.email(), CONTENT_REVIEWED, "Content", id.toString(),
                AuditLog.Result.SUCCESS, AuditService.details("status", request.status()));
        return contentMapper.toResponse(content);
    }

    @Transactional(readOnly = true)
    public ContentStatisticsResponse statistics() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        contentRepository.countByStatus().forEach(row -> byStatus.put(String.valueOf(row[0]), (Long) row[1]));
        Instant now = Instant.now();
        return new ContentStatisticsResponse(byStatus,
                contentRepository.countPublishedSince(now.minus(1, ChronoUnit.DAYS)),
                contentRepository.countPublishedSince(now.minus(7, ChronoUnit.DAYS)));
    }

    private Content find(UUID id) {
        return contentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Content", id));
    }
}
