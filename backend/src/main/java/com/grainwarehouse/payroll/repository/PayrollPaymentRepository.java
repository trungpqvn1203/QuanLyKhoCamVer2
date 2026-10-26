package com.grainwarehouse.payroll.repository;

import com.grainwarehouse.payroll.entity.PayrollPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Chỉ thêm và đọc — không sửa/xóa (BR-EPAY-04, trigger ở DB chặn nếu lỡ gọi). */
public interface PayrollPaymentRepository extends JpaRepository<PayrollPayment, Long> {

    @Query("""
            select p from PayrollPayment p left join fetch p.reversalOf
            where p.payrollId = :payrollId and p.employeeId = :employeeId order by p.id
            """)
    List<PayrollPayment> findByItem(@Param("payrollId") Long payrollId, @Param("employeeId") Long employeeId);

    // Lọc theo dòng lương để /api/payrolls/{month}/items/{employeeId}/payments/{id} không chạm được khoản của dòng khác
    Optional<PayrollPayment> findByIdAndPayrollIdAndEmployeeId(Long id, Long payrollId, Long employeeId);

    boolean existsByReversalOfId(Long reversalOfId);
}
