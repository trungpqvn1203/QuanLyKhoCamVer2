package com.grainwarehouse.employee.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.employee.dto.EmployeeSearchCriteria;
import com.grainwarehouse.employee.entity.Employee;
import org.springframework.data.jpa.domain.Specification;

/** Ghép tiêu chí lọc nhân viên thành điều kiện SQL; thêm tiêu chí mới = thêm một dòng ở đây. */
public final class EmployeeSpecifications {

    private EmployeeSpecifications() {
    }

    public static Specification<Employee> from(EmployeeSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.keywordContains(criteria, "code", "name", "phone"),
                CommonSpecifications.hasActive(criteria));
    }
}
