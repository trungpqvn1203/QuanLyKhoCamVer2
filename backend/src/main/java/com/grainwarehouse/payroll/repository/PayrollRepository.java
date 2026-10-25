package com.grainwarehouse.payroll.repository;

import com.grainwarehouse.payroll.entity.Payroll;
import com.grainwarehouse.payroll.entity.PayrollStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

    /** Bảng lương còn hiệu lực của tháng (nhiều nhất một — uk_payroll_finalized_period), kèm dòng + nhân viên. */
    @EntityGraph(attributePaths = {"items", "items.employee"})
    Optional<Payroll> findByPeriodAndStatus(LocalDate period, PayrollStatus status);

    List<Payroll> findByPeriodAndStatusOrderByIdDesc(LocalDate period, PayrollStatus status);

    /**
     * Bảng lương còn hiệu lực của tháng, CHỈ phần đầu (không nạp các dòng) — dùng khi chỉ cần id / trạng thái / cờ nợ,
     * ví dụ trả lương cho một nhân viên không cần kéo cả bảng lương của mọi người.
     */
    @Query("select p from Payroll p where p.period = :period and p.status = :status")
    Optional<Payroll> findHeader(@Param("period") LocalDate period, @Param("status") PayrollStatus status);
}
