package com.grainwarehouse.payroll.repository;

import com.grainwarehouse.payroll.entity.PayrollItem;
import com.grainwarehouse.payroll.entity.PayrollItemId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PayrollItemRepository extends JpaRepository<PayrollItem, PayrollItemId> {

    /** Một dòng lương kèm nhân viên (JOIN FETCH) — chỉ đọc. */
    @Query("""
            select i from PayrollItem i join fetch i.employee
            where i.id.payrollId = :payrollId and i.id.employeeId = :employeeId
            """)
    Optional<PayrollItem> findItem(@Param("payrollId") Long payrollId, @Param("employeeId") Long employeeId);

    /**
     * SELECT ... FOR UPDATE: giữ khóa dòng lương đến hết transaction (BR-EPAY-02). Phải gọi SAU khi đã khóa tháng và
     * khóa nhân viên (cùng thứ tự ở mọi nơi → không deadlock). Không JOIN FETCH nhân viên: khóa chỉ cần đặt trên dòng lương.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PayrollItem i where i.id.payrollId = :payrollId and i.id.employeeId = :employeeId")
    Optional<PayrollItem> findForUpdate(@Param("payrollId") Long payrollId, @Param("employeeId") Long employeeId);

    /**
     * BR-EPAY-01: các dòng lương còn nợ — của bảng lương ĐÃ CHỐT, có theo dõi công nợ (BR-EPAY-05), chưa trả đủ.
     * Cũ nhất lên đầu để màn công nợ liệt kê tháng nợ lâu nhất trước.
     */
    @Query("""
            select i from PayrollItem i join fetch i.payroll p join fetch i.employee
            where p.status = com.grainwarehouse.payroll.entity.PayrollStatus.FINALIZED
              and p.tracksDebt = true
              and i.paidAmount < i.totalAmount
            order by p.period
            """)
    List<PayrollItem> findUnpaid();
}
