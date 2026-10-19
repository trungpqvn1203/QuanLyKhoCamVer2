package com.grainwarehouse.payroll.repository;

import com.grainwarehouse.payroll.entity.Payroll;
import com.grainwarehouse.payroll.entity.PayrollStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    /** Bảng lương còn hiệu lực của tháng (nhiều nhất một — uk_payroll_finalized_period), kèm dòng + nhân viên. */
    @EntityGraph(attributePaths = {"items", "items.employee"})
    Optional<Payroll> findByPeriodAndStatus(LocalDate period, PayrollStatus status);

    List<Payroll> findByPeriodAndStatusOrderByIdDesc(LocalDate period, PayrollStatus status);
}
