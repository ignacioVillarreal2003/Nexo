package com.nexo.topic.domain.entity;

import com.nexo.topic.domain.entity.enums.CatalogStatus;
import com.nexo.topic.domain.entity.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fuente de información. Su {@link SourceType} define cómo la consulta el collector. */
@Entity
@Table(name = "sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Source extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private SourceType type;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    /** Parámetros propios del tipo de fuente, por ejemplo {@code maxItems} o {@code language}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> configuration;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    public static Source create(String name, SourceType type, String url, Map<String, Object> configuration) {
        Source source = new Source();
        source.name = name;
        source.type = type;
        source.url = url;
        source.configuration = configuration == null ? new LinkedHashMap<>() : new LinkedHashMap<>(configuration);
        source.status = CatalogStatus.ACTIVE;
        return source;
    }

    public void update(String name, SourceType type, String url, Map<String, Object> configuration) {
        this.name = name;
        this.type = type;
        this.url = url;
        this.configuration = configuration == null ? new LinkedHashMap<>() : new LinkedHashMap<>(configuration);
    }

    public void changeStatus(CatalogStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == CatalogStatus.ACTIVE;
    }
}
