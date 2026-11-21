package com.nexo.topic.domain.entity;

import com.nexo.topic.domain.entity.enums.CatalogStatus;
import com.nexo.topic.domain.entity.enums.TopicKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Área temática a la que se suscriben los usuarios y sobre la que se recopila contenido. */
@Entity
@Table(name = "topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Topic extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "slug", nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "icon", length = 40)
    private String icon;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keywords", nullable = false, columnDefinition = "jsonb")
    private List<String> keywords;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    /** Consulta para las fuentes de búsqueda; si falta se arma con las palabras clave. */
    @Column(name = "search_query", length = 500)
    private String searchQuery;

    /** Relevancia mínima (0–1) que exige la IA para publicar un contenido en este tópico. */
    @Column(name = "minimum_relevance", nullable = false, precision = 3, scale = 2)
    private BigDecimal minimumRelevance;

    @Column(name = "maximum_items_per_run", nullable = false)
    private int maximumItemsPerRun;

    /** Categorías de arXiv separadas por espacios, por ejemplo {@code cs.AI cs.LG}. */
    @Column(name = "arxiv_categories", length = 200)
    private String arxivCategories;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private TopicKind kind;

    /** ISO 3166-1 alfa-2 del país de un tópico COUNTRY o del medio de un OUTLET. */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "topic_sources",
            joinColumns = @JoinColumn(name = "topic_id"),
            inverseJoinColumns = @JoinColumn(name = "source_id"))
    private Set<Source> sources = new HashSet<>();

    public static Topic create(Category category, String slug, String name) {
        Topic topic = new Topic();
        topic.category = category;
        topic.slug = slug;
        topic.name = name;
        topic.keywords = new ArrayList<>();
        topic.status = CatalogStatus.ACTIVE;
        topic.minimumRelevance = new BigDecimal("0.55");
        topic.maximumItemsPerRun = 25;
        topic.kind = TopicKind.TOPIC;
        return topic;
    }

    public void update(Category category, String slug, String name, String description, String icon,
                       String imageUrl, List<String> keywords) {
        this.category = category;
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.imageUrl = imageUrl;
        this.keywords = keywords == null ? new ArrayList<>() : new ArrayList<>(keywords);
    }

    public void configureSearch(String searchQuery, BigDecimal minimumRelevance, int maximumItemsPerRun,
                                String arxivCategories) {
        this.searchQuery = searchQuery;
        this.minimumRelevance = minimumRelevance;
        this.maximumItemsPerRun = maximumItemsPerRun;
        this.arxivCategories = arxivCategories;
    }

    public void classify(TopicKind kind, String countryCode) {
        this.kind = kind == null ? TopicKind.TOPIC : kind;
        this.countryCode = countryCode == null || countryCode.isBlank() ? null : countryCode.trim().toUpperCase();
    }

    public void replaceSources(Collection<Source> sources) {
        this.sources.clear();
        this.sources.addAll(sources);
    }

    public void changeStatus(CatalogStatus status) {
        this.status = status;
    }
}
