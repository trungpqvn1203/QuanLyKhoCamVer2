package com.grainwarehouse.employee.repository;

import com.grainwarehouse.employee.entity.Employee;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    /** Dòng của bảng công tháng (module attendance) — không phân trang: một kho có vài chục người. */
    List<Employee> findByActiveTrue(Sort sort);

    /**
     * SELECT ... FOR UPDATE: giữ khóa dòng nhân viên đến hết transaction. Mọi thao tác đổi số dư tạm ứng (ứng tiền,
     * hoàn khoản ứng, trừ lương) phải lấy nhân viên bằng hàm này TRƯỚC khi kiểm tra số dư — kiểm tra rồi mới khóa thì
     * hai request cùng thấy "đủ dư" và cùng trừ (số dư âm). Cùng nguyên tắc với khóa phiếu kho (BR-IMP-14).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findForUpdate(@Param("id") Long id);

    /** Nhân viên còn dư tạm ứng — màn công nợ nhân viên. */
    List<Employee> findByAdvanceBalanceGreaterThan(BigDecimal amount);
}
