package com.grainwarehouse.pricelist.dto;

import com.grainwarehouse.pricelist.entity.PriceList;

import java.time.Instant;

/** itemCount: số SP đã có giá trong bảng; customerCount: số khách đang áp dụng bảng giá này. */
public record PriceListResponse(
        Long id,
        String name,
        String description,
        boolean active,
        long itemCount,
        long customerCount,
        Instant createdAt,
        Instant updatedAt) {

    public static PriceListResponse from(PriceList list, long itemCount, long customerCount) {
        return new PriceListResponse(list.getId(), list.getName(), list.getDescription(), list.isActive(),
                itemCount, customerCount, list.getCreatedAt(), list.getUpdatedAt());
    }
}
