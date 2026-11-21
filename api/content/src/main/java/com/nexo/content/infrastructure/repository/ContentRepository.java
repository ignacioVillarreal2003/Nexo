package com.nexo.content.infrastructure.repository;

import com.nexo.content.domain.entity.Content;
import com.nexo.content.domain.entity.enums.BriefStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentRepository extends JpaRepository<Content, UUID>, JpaSpecificationExecutor<Content> {

    /** Vecino semántico más cercano: id y similitud coseno. */
    interface Neighbor {
        UUID getId();

        String getTitle();

        String getSummary();

        Instant getPublicationDate();

        double getSimilarity();
    }

    /** Contenido publicado de un tópico, con su puntuación y su embedding como texto. */
    interface CandidateRow {
        UUID getId();

        String getTitle();

        String getSummary();

        String getTitleEn();

        String getSummaryEn();

        String getOriginalUrl();

        String getSourceName();

        String getImageUrl();

        Instant getPublicationDate();

        Instant getCollectedAt();

        String getRelation();

        UUID getRelatedContentId();

        double getScore();

        String getEmbedding();
    }

    @EntityGraph(attributePaths = "topics")
    Optional<Content> findByUrlHash(String urlHash);

    // Sin @EntityGraph: un fetch de colección con LIMIT obliga a Hibernate a paginar en memoria (HHH90003004).
    Optional<Content> findFirstByContentHashAndCollectedAtAfterOrderByCollectedAtAsc(String contentHash, Instant since);

    @Query("select c.urlHash from Content c where c.urlHash in :hashes")
    List<String> findKnownUrlHashes(Collection<String> hashes);

    /** Contenido reciente cuyo SimHash está a distancia de Hamming {@code <= maximumDistance}. */
    @Query(value = """
            select id from contents
            where collected_at > :since
              and bit_count(cast(simhash # :simhash as bit(64))) <= :maximumDistance
            order by bit_count(cast(simhash # :simhash as bit(64))), collected_at
            limit 1""", nativeQuery = true)
    Optional<UUID> findNearTextualDuplicate(long simhash, int maximumDistance, Instant since);

    @Query(value = """
            select id, title, summary, publication_date as publicationDate,
                   1 - (embedding <=> cast(:embedding as vector)) as similarity
            from contents
            where status = 'PUBLISHED' and embedding is not null and collected_at > :since
            order by embedding <=> cast(:embedding as vector)
            limit 1""", nativeQuery = true)
    Optional<Neighbor> findNearestPublished(String embedding, Instant since);

    /**
     * Candidatos de un tópico ordenados por puntuación: 50 % relevancia para el tópico, 30 % calidad
     * y 20 % frescura (decae linealmente dentro de la ventana).
     */
    @Query(value = """
            select c.id, c.title, c.summary, c.title_en as titleEn, c.summary_en as summaryEn,
                   c.original_url as originalUrl, c.source_name as sourceName,
                   c.image_url as imageUrl, c.publication_date as publicationDate, c.collected_at as collectedAt,
                   c.relation, c.related_content_id as relatedContentId,
                   cast(c.embedding as text) as embedding,
                   (0.5 * ct.relevance + 0.3 * coalesce(c.quality_score, 0.5)
                    + 0.2 * greatest(0, 1 - extract(epoch from (now() - coalesce(c.publication_date, c.collected_at)))
                                           / :windowSeconds)) as score
            from content_topics ct
            join contents c on c.id = ct.content_id
            where ct.topic_id = :topicId and c.status = 'PUBLISHED' and c.collected_at > :since
            order by score desc
            limit :limit""", nativeQuery = true)
    List<CandidateRow> findCandidates(UUID topicId, Instant since, double windowSeconds, int limit);

    interface PreviewRow {
        UUID getId();

        String getTitle();

        String getSummary();

        String getSourceName();

        String getOriginalUrl();

        String getImageUrl();

        Instant getPublishedAt();
    }

    /** Lo último publicado para un tópico, del más reciente al más viejo. */
    @Query(value = """
            select c.id, c.title, c.summary, c.source_name as sourceName, c.original_url as originalUrl,
                   c.image_url as imageUrl, coalesce(c.publication_date, c.collected_at) as publishedAt
            from content_topics ct
            join contents c on c.id = ct.content_id
            where ct.topic_id = :topicId and c.status = 'PUBLISHED'
            order by coalesce(c.publication_date, c.collected_at) desc
            limit :limit""", nativeQuery = true)
    List<PreviewRow> findLatestPublished(UUID topicId, int limit);

    @Query(value = """
            select count(*) from content_topics ct join contents c on c.id = ct.content_id
            where ct.topic_id = :topicId and c.status = 'PUBLISHED' and c.collected_at > :since""", nativeQuery = true)
    long countPublishedForTopicSince(UUID topicId, Instant since);

    /** Informes pedidos y todavía no escritos, en el orden en que se pidieron. */
    @Query("select c.id from Content c where c.briefStatus = :status order by c.briefRequestedAt")
    List<UUID> findIdsByBriefStatus(BriefStatus status, Pageable pageable);

    @Query("select c.status, count(c) from Content c group by c.status")
    List<Object[]> countByStatus();

    @Query("select count(c) from Content c where c.status = com.nexo.content.domain.entity.enums.ContentStatus.PUBLISHED and c.collectedAt > :since")
    long countPublishedSince(Instant since);
}
