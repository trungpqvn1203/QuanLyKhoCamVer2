package com.grainwarehouse.customer.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.customer.dto.CustomerSearchCriteria;
import com.grainwarehouse.customer.entity.Customer;
import org.springframework.data.jpa.domain.Specification;

/** Ghép tiêu chí lọc khách thành điều kiện SQL; thêm tiêu chí mới = thêm một dòng ở đây. */
public final class CustomerSpecifications {

    private CustomerSpecifications() {
    }

    public static Specification<Customer> from(CustomerSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.keywordContains(criteria, "code", "name", "phone"),
                CommonSpecifications.equal("type", criteria.type()),
                CommonSpecifications.hasActive(criteria));
    }
}
