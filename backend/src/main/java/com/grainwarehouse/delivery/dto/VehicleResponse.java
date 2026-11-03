package com.grainwarehouse.delivery.dto;

import com.grainwarehouse.delivery.entity.Vehicle;

import java.math.BigDecimal;
import java.time.Instant;

public record VehicleResponse(
        Long id,
        String plateNumber,
        String name,
        BigDecimal capacityTons,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getPlateNumber(), vehicle.getName(),
                vehicle.getCapacityTons(), vehicle.isActive(), vehicle.getCreatedAt(), vehicle.getUpdatedAt());
    }
}
