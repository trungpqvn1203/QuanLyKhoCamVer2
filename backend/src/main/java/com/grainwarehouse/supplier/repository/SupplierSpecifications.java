package com.grainwarehouse.supplier.repository;

import com.grainwarehouse.common.repository.CommonSpecifications;
import com.grainwarehouse.supplier.dto.SupplierSearchCriteria;
import com.grainwarehouse.supplier.entity.Supplier;
import org.springframework.data.jpa.domain.Specification;

/** Ghép tiêu chí lọc NCC thành điều kiện SQL; thêm tiêu chí mới = thêm một dòng ở đây. */
public final class SupplierSpecifications {

    private SupplierSpecifications() {
    }

    public static Specification<Supplier> from(SupplierSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.keywordContains(criteria.keyword(), "code", "name"),
                CommonSpecifications.hasActive(criteria.active()));
    }
}
