package com.grainwarehouse.cashbook.dto;

import com.grainwarehouse.cashbook.entity.CashCategory;
import com.grainwarehouse.cashbook.entity.CashDirection;

import java.time.Instant;

public record CashCategoryResponse(
        Long id,
        String name,
        CashDirection direction,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static CashCategoryResponse from(CashCategory category) {
        return new CashCategoryResponse(category.getId(), category.getName(), category.getDirection(),
                category.isActive(), category.getCreatedAt(), category.getUpdatedAt());
    }
}
