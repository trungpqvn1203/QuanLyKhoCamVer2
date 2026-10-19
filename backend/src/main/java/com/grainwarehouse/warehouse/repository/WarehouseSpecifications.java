package com.grainwarehouse.warehouse.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.warehouse.dto.WarehouseSearchCriteria;
import com.grainwarehouse.warehouse.entity.Warehouse;
import org.springframework.data.jpa.domain.Specification;

/** Ghép tiêu chí lọc kho thành điều kiện SQL; thêm tiêu chí mới = thêm một dòng ở đây. */
public final class WarehouseSpecifications {

    private WarehouseSpecifications() {
    }

    public static Specification<Warehouse> from(WarehouseSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.keywordContains(criteria, "code", "name"),
                CommonSpecifications.hasActive(criteria));
    }
}
