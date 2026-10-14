package com.grainwarehouse.product.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Danh mục sản phẩm: cám gà, cám heo, cám cá... (BR-CAT-01..03). */
@Getter
@Setter
@Entity
@Table(name = "category")
public class Category extends AuditableEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean active = true;
}
