package com.grainwarehouse.delivery.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.delivery.dto.VehicleSearchCriteria;
import com.grainwarehouse.delivery.entity.Vehicle;
import org.springframework.data.jpa.domain.Specification;

public final class VehicleSpecifications {

    private VehicleSpecifications() {
    }

    public static Specification<Vehicle> from(VehicleSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.keywordContains(criteria, "plateNumber", "name"),
                CommonSpecifications.hasActive(criteria));
    }
}
