package com.nexo.content.application.service;

import com.nexo.content.domain.config.ApplicationProperties;
import com.nexo.content.domain.dto.response.CandidateResponse;
import com.nexo.content.domain.util.TextFingerprint;
import com.nexo.content.domain.util.UrlNormalizer;
import com.nexo.content.domain.util.Vectors;
import com.nexo.content.infrastructure.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Lecturas para otros servicios: candidatos para las entregas y URLs ya conocidas para el collector. */
@Service
@RequiredArgsConstructor
public class CandidateService {

    private static final int MAXIMUM_KNOWN_URLS = 1000;

    private final ContentRepository contentRepository;
    private final ApplicationProperties properties;

    @Transactional(readOnly = true)
    public List<CandidateResponse> candidates(UUID topicId, int limit) {
        Instant since = Instant.now().minus(properties.candidates().window());
        double windowSeconds = properties.candidates().window().toSeconds();
        return contentRepository.findCandidates(topicId, since, windowSeconds, limit).stream()
                .map(row -> new CandidateResponse(row.getId(), row.getTitle(), row.getSummary(), row.getTitleEn(), row.getSummaryEn(),
                        row.getOriginalUrl(),
                        row.getSourceName(), row.getImageUrl(), row.getPublicationDate(), row.getCollectedAt(),
                        row.getRelation(), row.getRelatedContentId(), Math.round(row.getScore() * 1000.0) / 1000.0,
                        Vectors.fromLiteral(row.getEmbedding())))
                .toList();
    }

    /** Devuelve las URLs que ya están en la plataforma, para que el collector no descargue su texto otra vez. */
    @Transactional(readOnly = true)
    public List<String> knownUrls(List<String> urls) {
        Map<String, String> byHash = new LinkedHashMap<>();
        urls.stream().limit(MAXIMUM_KNOWN_URLS)
                .forEach(url -> byHash.put(TextFingerprint.sha256(UrlNormalizer.normalize(url)), url));
        if (byHash.isEmpty()) {
            return List.of();
        }
        return contentRepository.findKnownUrlHashes(byHash.keySet()).stream().map(byHash::get).toList();
    }
}
