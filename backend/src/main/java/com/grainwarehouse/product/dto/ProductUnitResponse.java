package com.grainwarehouse.product.dto;

import com.grainwarehouse.product.entity.ProductUnit;

import java.math.BigDecimal;
import java.time.Instant;

/** fixedNetWeightKg chỉ có ở đơn vị hệ thống (Kg = 1); null bị bỏ khỏi JSON. */
public record ProductUnitResponse(
        Long id,
        String name,
        BigDecimal fixedNetWeightKg,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductUnitResponse from(ProductUnit unit) {
        return new ProductUnitResponse(
                unit.getId(),
                unit.getName(),
                unit.getFixedNetWeightKg(),
                unit.isActive(),
                unit.getCreatedAt(),
                unit.getUpdatedAt());
    }
}
