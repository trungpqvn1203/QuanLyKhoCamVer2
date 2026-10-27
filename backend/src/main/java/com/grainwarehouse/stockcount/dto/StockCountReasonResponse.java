package com.grainwarehouse.stockcount.dto;

import com.grainwarehouse.stockcount.entity.StockCountReason;

import java.time.Instant;

public record StockCountReasonResponse(
        Long id,
        String name,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static StockCountReasonResponse from(StockCountReason reason) {
        return new StockCountReasonResponse(reason.getId(), reason.getName(), reason.isActive(),
                reason.getCreatedAt(), reason.getUpdatedAt());
    }
}
