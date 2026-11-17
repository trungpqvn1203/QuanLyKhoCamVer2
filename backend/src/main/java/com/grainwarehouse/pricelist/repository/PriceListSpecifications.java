package com.grainwarehouse.pricelist.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.pricelist.dto.PriceListSearchCriteria;
import com.grainwarehouse.pricelist.entity.PriceList;
import org.springframework.data.jpa.domain.Specification;

public final class PriceListSpecifications {

    private PriceListSpecifications() {
    }

    public static Specification<PriceList> from(PriceListSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.keywordContains(criteria, "name", "description"),
                CommonSpecifications.hasActive(criteria));
    }
}
