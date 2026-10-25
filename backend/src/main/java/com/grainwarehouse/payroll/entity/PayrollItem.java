package com.grainwarehouse.payroll.entity;

import com.grainwarehouse.employee.entity.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Một dòng của bảng lương đã chốt: bản chụp số công, số tấn (bốc xuống, giao hàng) và ĐƠN GIÁ lúc chốt (BR-PAY-02).
 * Phần bản chụp bất biến: không setter, mọi cột bản chụp updatable = false — đổi đơn giá nhân viên sau này không đổi
 * được dòng này. Chỉ paidAmount (số đã trả, BR-EPAY-01) đổi được, qua addPaidAmount sau khi service khóa dòng.
 * Không dùng @Immutable của Hibernate vì nó chặn luôn việc cập nhật paidAmount.
 */
@Getter
@Entity
@Table(name = "payroll_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayrollItem {

    @EmbeddedId
    private PayrollItemId id;

    // @MapsId: payroll_id lấy từ payroll cha sau khi cha được INSERT (id cha do DB sinh)
    @MapsId("payrollId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_id")
    private Payroll payroll;

    @MapsId("employeeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "work_units", nullable = false, precision = 5, scale = 1, updatable = false)
    private BigDecimal workUnits;

    @Column(name = "unload_tons", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal unloadTons;

    @Column(name = "delivery_tons", nullable = false, precision = 12, scale = 3, updatable = false)
    private BigDecimal deliveryTons;

    @Column(name = "daily_wage", precision = 15, scale = 2, updatable = false)
    private BigDecimal dailyWage;

    @Column(name = "unload_ton_rate", precision = 15, scale = 2, updatable = false)
    private BigDecimal unloadTonRate;

    @Column(name = "delivery_ton_rate", precision = 15, scale = 2, updatable = false)
    private BigDecimal deliveryTonRate;

    @Column(name = "day_amount", nullable = false, precision = 18, updatable = false)
    private BigDecimal dayAmount;

    @Column(name = "unload_amount", nullable = false, precision = 18, updatable = false)
    private BigDecimal unloadAmount;

    @Column(name = "delivery_amount", nullable = false, precision = 18, updatable = false)
    private BigDecimal deliveryAmount;

    @Column(name = "total_amount", nullable = false, precision = 18, updatable = false)
    private BigDecimal totalAmount;

    // BR-EPAY-01: tổng đã tất toán (tiền mặt + chuyển khoản + trừ tạm ứng) — snapshot của payroll_payment.
    // CHECK ck_payroll_item_paid: 0 <= paid <= total
    @Column(name = "paid_amount", nullable = false, precision = 18)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    PayrollItem(Payroll payroll, Employee employee, BigDecimal workUnits, BigDecimal unloadTons,
                BigDecimal deliveryTons, BigDecimal dayAmount, BigDecimal unloadAmount, BigDecimal deliveryAmount) {
        this.id = new PayrollItemId(null, employee.getId());
        this.payroll = payroll;
        this.employee = employee;
        this.workUnits = workUnits;
        this.unloadTons = unloadTons;
        this.deliveryTons = deliveryTons;
        this.dailyWage = employee.getDailyWage();
        this.unloadTonRate = employee.getUnloadTonRate();
        this.deliveryTonRate = employee.getDeliveryTonRate();
        this.dayAmount = dayAmount;
        this.unloadAmount = unloadAmount;
        this.deliveryAmount = deliveryAmount;
        this.totalAmount = dayAmount.add(unloadAmount).add(deliveryAmount);
    }

    /** Còn nợ nhân viên = lương chốt − đã trả. Có tính nợ hay không do Payroll.tracksDebt quyết định (BR-EPAY-05). */
    public BigDecimal getOutstandingAmount() {
        return totalAmount.subtract(paidAmount);
    }

    /** Cộng (trả) hoặc trừ (hoàn). Không tự kiểm tra vượt nợ — service kiểm tra sau khi khóa; CHECK ở DB chặn cuối. */
    public void addPaidAmount(BigDecimal delta) {
        paidAmount = paidAmount.add(delta);
    }
}
