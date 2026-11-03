package com.grainwarehouse.delivery.dto;

import com.grainwarehouse.delivery.entity.DeliveryTrip;
import com.grainwarehouse.delivery.entity.DeliveryTripStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Dòng danh sách chuyến — không kèm điểm giao, chỉ số điểm và tổng tấn. */
public record DeliveryTripSummaryResponse(
        Long id,
        String code,
        LocalDate tripDate,
        DeliveryTripStatus status,
        Long vehicleId,
        String vehiclePlate,
        BigDecimal capacityTons,
        Long driverId,
        String driverName,
        int stopCount,
        BigDecimal totalTons,
        BigDecimal overloadTons,
        Instant dispatchedAt) {

    public static DeliveryTripSummaryResponse from(DeliveryTrip trip, int stopCount, BigDecimal totalTons) {
        BigDecimal capacity = trip.getVehicle().getCapacityTons();
        return new DeliveryTripSummaryResponse(
                trip.getId(),
                trip.getCode(),
                trip.getTripDate(),
                trip.getStatus(),
                trip.getVehicle().getId(),
                trip.getVehicle().getPlateNumber(),
                capacity,
                trip.getDriver().getId(),
                trip.getDriver().getName(),
                stopCount,
                totalTons,
                DeliveryTripResponse.overload(capacity, totalTons),
                trip.getDispatchedAt());
    }
}
