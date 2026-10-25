package com.grainwarehouse.employee.entity;

import com.grainwarehouse.common.entity.BaseEntity;
import com.grainwarehouse.sales.entity.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Một lần ứng tiền (hoặc hoàn khoản ứng ghi nhầm) cho nhân viên — BẤT BIẾN (BR-EPAY-04), cùng ba lớp bảo vệ
 * như SalePayment. Số dư tạm ứng nằm ở Employee.advanceBalance (snapshot của nhật ký này và của các lần trừ lương).
 * Hình thức tiền (PaymentMethod) dùng chung với thu/trả tiền ở phiếu bán/nhập: cùng hai giá trị.
 */
@Getter
@Entity
@Immutable
@Table(name = "employee_advance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeAdvance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EmployeeAdvanceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    // Luôn > 0; chiều (ứng/hoàn) nằm ở type
    @Column(nullable = false, precision = 18, scale = 0)
    private BigDecimal amount;

    @Column(name = "advance_date", nullable = false)
    private LocalDate advanceDate;

    // Ghi chú khoản ứng, hoặc lý do hoàn
    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private EmployeeAdvance reversalOf;

    public static EmployeeAdvance advance(Employee employee, PaymentMethod method, BigDecimal amount,
                                          LocalDate advanceDate, String note) {
        return new EmployeeAdvance(employee, EmployeeAdvanceType.ADVANCE, method, amount, advanceDate, note, null);
    }

    /** Hoàn toàn bộ khoản ứng gốc: cùng hình thức, cùng số tiền. */
    public static EmployeeAdvance reversalOf(EmployeeAdvance original, LocalDate date, String reason) {
        return new EmployeeAdvance(original.employee, EmployeeAdvanceType.REVERSAL, original.method, original.amount,
                date, reason, original);
    }

    private EmployeeAdvance(Employee employee, EmployeeAdvanceType type, PaymentMethod method, BigDecimal amount,
                            LocalDate advanceDate, String note, EmployeeAdvance reversalOf) {
        this.employee = employee;
        this.type = type;
        this.method = method;
        this.amount = amount;
        this.advanceDate = advanceDate;
        this.note = note;
        this.reversalOf = reversalOf;
    }

    /** Ảnh hưởng tới số dư tạm ứng: ứng cộng, hoàn trừ. */
    public BigDecimal signedAmount() {
        return type == EmployeeAdvanceType.ADVANCE ? amount : amount.negate();
    }
}
