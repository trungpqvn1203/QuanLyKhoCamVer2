package com.grainwarehouse.product.dto;

import com.grainwarehouse.product.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String code,
        String name,
        Long categoryId,
        String categoryName,
        Long unitId,
        String unitName,
        BigDecimal netWeightKg,
        String description,
        Integer minStockQuantity,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    /** Phải gọi bên trong transaction vì category là LAZY. */
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getUnit().getId(),
                product.getUnit().getName(),
                product.getNetWeightKg(),
                product.getDescription(),
                product.getMinStockQuantity(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
