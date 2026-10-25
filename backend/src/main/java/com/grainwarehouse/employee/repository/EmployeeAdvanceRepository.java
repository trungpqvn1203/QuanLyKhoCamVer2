package com.grainwarehouse.employee.repository;

import com.grainwarehouse.employee.entity.EmployeeAdvance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Chỉ thêm và đọc — không sửa/xóa (BR-EPAY-04, trigger ở DB chặn nếu lỡ gọi). */
public interface EmployeeAdvanceRepository extends JpaRepository<EmployeeAdvance, Long> {

    /** Mới nhất lên đầu; JOIN FETCH khoản gốc của dòng hoàn để dựng response không phát sinh N+1. */
    @Query("""
            select a from EmployeeAdvance a left join fetch a.reversalOf
            where a.employee.id = :employeeId order by a.id desc
            """)
    List<EmployeeAdvance> findByEmployeeId(@Param("employeeId") Long employeeId);

    // Lọc theo nhân viên để /api/employees/{id}/advances/{advanceId} không chạm được khoản ứng của người khác
    Optional<EmployeeAdvance> findByIdAndEmployeeId(Long id, Long employeeId);

    boolean existsByReversalOfId(Long reversalOfId);
}
