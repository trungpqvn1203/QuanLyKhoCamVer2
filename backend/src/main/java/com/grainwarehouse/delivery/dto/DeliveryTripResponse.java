package com.grainwarehouse.delivery.dto;

import com.grainwarehouse.delivery.entity.DeliveryTrip;
import com.grainwarehouse.delivery.entity.DeliveryTripStatus;
import com.grainwarehouse.delivery.entity.Vehicle;
import com.grainwarehouse.employee.entity.Employee;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Chi tiết chuyến. totalTons = tổng tấn các điểm giao (không lưu, tính từ SL × khối lượng SP).
 * overloadTons chỉ có khi xe có tải trọng và chuyến vượt tải (BR-DLV-04: cảnh báo, không chặn).
 */
public record DeliveryTripResponse(
        Long id,
        String code,
        LocalDate tripDate,
        DeliveryTripStatus status,
        Long vehicleId,
        String vehiclePlate,
        String vehicleName,
        BigDecimal capacityTons,
        Long driverId,
        String driverCode,
        String driverName,
        String driverPhone,
        String note,
        int stopCount,
        BigDecimal totalTons,
        BigDecimal overloadTons,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Instant dispatchedAt,
        String dispatchedBy,
        List<DeliveryOrderResponse> stops) {

    public static DeliveryTripResponse from(DeliveryTrip trip, List<DeliveryOrderResponse> stops) {
        Vehicle vehicle = trip.getVehicle();
        Employee driver = trip.getDriver();
        BigDecimal totalTons = stops.stream().map(DeliveryOrderResponse::totalTons)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DeliveryTripResponse(
                trip.getId(),
                trip.getCode(),
                trip.getTripDate(),
                trip.getStatus(),
                vehicle.getId(),
                vehicle.getPlateNumber(),
                vehicle.getName(),
                vehicle.getCapacityTons(),
                driver.getId(),
                driver.getCode(),
                driver.getName(),
                driver.getPhone(),
                trip.getNote(),
                stops.size(),
                totalTons,
                overload(vehicle.getCapacityTons(), totalTons),
                trip.getCreatedBy(),
                trip.getCreatedAt(),
                trip.getUpdatedAt(),
                trip.getDispatchedAt(),
                trip.getDispatchedBy(),
                stops);
    }

    /** Số tấn vượt tải trọng; null khi xe không ghi tải trọng hoặc chưa vượt. */
    static BigDecimal overload(BigDecimal capacityTons, BigDecimal totalTons) {
        if (capacityTons == null || totalTons.compareTo(capacityTons) <= 0) {
            return null;
        }
        return totalTons.subtract(capacityTons);
    }
}
