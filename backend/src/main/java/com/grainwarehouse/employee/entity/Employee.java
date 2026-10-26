package com.grainwarehouse.employee.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;

import java.math.BigDecimal;

/** Nhân viên (BR-EMP-01..04). */
@Getter
@Setter
@Entity
@Table(name = "employee")
public class Employee extends AuditableEntity {

    // DB tự sinh qua DEFAULT nextval(...) (BR-EMP-01) — cùng cách với Customer.code
    @Generated
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 20, insertable = false, updatable = false)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    // Tùy chọn; có thì luôn ở dạng đã chuẩn hóa (BR-EMP-03)
    @Column(length = 20)
    private String phone;

    // BR-EMP-02: null = không hưởng lương theo ngày
    @Column(name = "daily_wage", precision = 15, scale = 2)
    private BigDecimal dailyWage;

    // BR-EMP-02, BR-EMP-04: đơn giá tấn bốc xuống hàng; null = không hưởng theo cách này
    @Column(name = "unload_ton_rate", precision = 15, scale = 2)
    private BigDecimal unloadTonRate;

    // BR-EMP-04: đơn giá tấn đi giao hàng, khác đơn giá bốc xuống; null = không hưởng theo cách này
    @Column(name = "delivery_ton_rate", precision = 15, scale = 2)
    private BigDecimal deliveryTonRate;

    // BR-EPAY-03: số tiền đã ứng và chưa trừ vào lương — snapshot của employee_advance và các lần trừ lương
    // (payroll_payment ADVANCE_DEDUCTION). Không bao giờ âm (ck_employee_advance_balance). Không có setter:
    // chỉ đổi qua addAdvanceBalance, sau khi service khóa dòng nhân viên FOR UPDATE.
    @Setter(AccessLevel.NONE)
    @Column(name = "advance_balance", nullable = false, precision = 18, scale = 0)
    private BigDecimal advanceBalance = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    /** Cộng (ứng thêm) hoặc trừ (trừ lương, hoàn khoản ứng). Không tự kiểm tra âm — service kiểm tra, CHECK ở DB chặn cuối. */
    public void addAdvanceBalance(BigDecimal delta) {
        advanceBalance = advanceBalance.add(delta);
    }
}
