package com.grainwarehouse.delivery.dto;

import com.grainwarehouse.common.search.HasKeyword;
import com.grainwarehouse.delivery.entity.DeliveryTripStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** GET /api/delivery-trips?keyword=&status=&vehicleId=&driverId=&tripDateFrom=&tripDateTo= — null = không lọc. */
public record DeliveryTripSearchCriteria(
        String keyword,
        DeliveryTripStatus status,
        Long vehicleId,
        Long driverId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tripDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tripDateTo) implements HasKeyword {
}
