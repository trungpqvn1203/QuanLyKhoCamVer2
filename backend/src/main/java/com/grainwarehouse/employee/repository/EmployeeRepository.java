package com.grainwarehouse.employee.repository;

import com.grainwarehouse.employee.entity.Employee;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /** Dòng của bảng công tháng (module attendance) — không phân trang: một kho có vài chục người. */
    List<Employee> findByActiveTrue(Sort sort);
}
