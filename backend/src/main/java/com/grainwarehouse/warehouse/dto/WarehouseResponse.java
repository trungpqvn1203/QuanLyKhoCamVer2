package com.grainwarehouse.warehouse.dto;

import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.entity.WarehouseType;

import java.time.Instant;

public record WarehouseResponse(
        Long id,
        String code,
        String name,
        WarehouseType type,
        String address,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getType(),
                warehouse.getAddress(),
                warehouse.isActive(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt());
    }
}
