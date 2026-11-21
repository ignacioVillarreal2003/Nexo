package com.nexo.topic.domain.entity;

import com.nexo.topic.domain.entity.enums.CatalogStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

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

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    public static Category create(String slug, String name) {
        Category category = new Category();
        category.slug = slug;
        category.name = name;
        category.status = CatalogStatus.ACTIVE;
        return category;
    }

    public void update(String slug, String name, String description, String icon, String imageUrl, int sortOrder) {
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.imageUrl = imageUrl;
        this.sortOrder = sortOrder;
    }

    public void changeStatus(CatalogStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == CatalogStatus.ACTIVE;
    }
}
