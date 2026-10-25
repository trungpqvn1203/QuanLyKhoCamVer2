package com.grainwarehouse.payroll.entity;

import com.grainwarehouse.common.entity.BaseEntity;
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
 * Một lần trả (hoặc hoàn) lương cho một dòng lương — BẤT BIẾN (BR-EPAY-04), cùng ba lớp bảo vệ như SalePayment:
 * không setter, @Immutable, trigger trg_payroll_payment_immutable ở DB.
 * Gắn với dòng lương bằng hai cột (payroll_id, employee_id) thay vì quan hệ JPA: FK kép ở DB đã đảm bảo toàn vẹn,
 * và không cần nạp dòng lương mỗi lần đọc lịch sử.
 */
@Getter
@Entity
@Immutable
@Table(name = "payroll_payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollPayment extends BaseEntity {

    @Column(name = "payroll_id", nullable = false)
    private Long payrollId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PayrollPaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayrollPaymentMethod method;

    // Luôn > 0; chiều (trả/hoàn) nằm ở type
    @Column(nullable = false, precision = 18, scale = 0)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    // Ghi chú khoản trả, hoặc lý do hoàn
    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private PayrollPayment reversalOf;

    public static PayrollPayment payment(Long payrollId, Long employeeId, PayrollPaymentMethod method,
                                         BigDecimal amount, LocalDate paymentDate, String note) {
        return new PayrollPayment(payrollId, employeeId, PayrollPaymentType.PAYMENT, method, amount, paymentDate,
                note, null);
    }

    /** Hoàn toàn bộ khoản gốc: cùng hình thức, cùng số tiền (BR-EPAY-04). */
    public static PayrollPayment reversalOf(PayrollPayment original, LocalDate date, String reason) {
        return new PayrollPayment(original.payrollId, original.employeeId, PayrollPaymentType.REVERSAL,
                original.method, original.amount, date, reason, original);
    }

    private PayrollPayment(Long payrollId, Long employeeId, PayrollPaymentType type, PayrollPaymentMethod method,
                           BigDecimal amount, LocalDate paymentDate, String note, PayrollPayment reversalOf) {
        this.payrollId = payrollId;
        this.employeeId = employeeId;
        this.type = type;
        this.method = method;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.note = note;
        this.reversalOf = reversalOf;
    }

    /** Ảnh hưởng tới số đã trả của dòng lương: trả cộng, hoàn trừ. */
    public BigDecimal signedAmount() {
        return type == PayrollPaymentType.PAYMENT ? amount : amount.negate();
    }

    public boolean isAdvanceDeduction() {
        return method == PayrollPaymentMethod.ADVANCE_DEDUCTION;
    }
}
