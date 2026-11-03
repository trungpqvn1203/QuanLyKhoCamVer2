package com.grainwarehouse.delivery.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.delivery.dto.DeliveryTripSearchCriteria;
import com.grainwarehouse.delivery.entity.DeliveryTrip;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class DeliveryTripSpecifications {

    private DeliveryTripSpecifications() {
    }

    public static Specification<DeliveryTrip> from(DeliveryTripSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.keywordContains(criteria, "code", "vehicle.plateNumber", "driver.name"),
                CommonSpecifications.equal("status", criteria.status()),
                CommonSpecifications.equal("vehicle.id", criteria.vehicleId()),
                CommonSpecifications.equal("driver.id", criteria.driverId()),
                CommonSpecifications.<DeliveryTrip, LocalDate>between(
                        "tripDate", criteria.tripDateFrom(), criteria.tripDateTo()));
    }
}
