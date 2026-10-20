package com.grainwarehouse.payroll.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import com.grainwarehouse.employee.entity.Employee;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bảng lương tháng đã chốt (BR-PAY-02..03). Chỉ có hai trạng thái: FINALIZED → CANCELLED (một chiều).
 * Không có setStatus: đổi trạng thái chỉ qua cancel(), service kiểm tra trước khi gọi.
 */
@Getter
@Entity
@Table(name = "payroll")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payroll extends AuditableEntity {

    // Ngày 1 của tháng (CHECK ck_payroll_period_first_day)
    @Column(nullable = false, updatable = false)
    private LocalDate period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayrollStatus status;

    @Column(name = "total_amount", nullable = false, precision = 18, updatable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "finalized_at", nullable = false, updatable = false)
    private Instant finalizedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    // PERSIST: lưu bảng lương là lưu luôn các dòng. Không orphanRemoval: dòng đã chốt không bao giờ bị gỡ
    @OneToMany(mappedBy = "payroll", cascade = CascadeType.PERSIST)
    private List<PayrollItem> items = new ArrayList<>();

    public static Payroll create(YearMonth month, Instant now) {
        Payroll payroll = new Payroll();
        payroll.period = month.atDay(1);
        payroll.status = PayrollStatus.FINALIZED;
        payroll.finalizedAt = now;
        return payroll;
    }

    public void addItem(Employee employee, BigDecimal workUnits, BigDecimal tons,
                        BigDecimal dayAmount, BigDecimal tonAmount) {
        PayrollItem item = new PayrollItem(this, employee, workUnits, tons, dayAmount, tonAmount);
        items.add(item);
        totalAmount = totalAmount.add(item.getTotalAmount());
    }

    public void cancel(String reason, Instant now) {
        status = PayrollStatus.CANCELLED;
        cancelReason = reason;
        cancelledAt = now;
    }

    public YearMonth month() {
        return YearMonth.from(period);
    }

    public List<PayrollItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
