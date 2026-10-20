package com.grainwarehouse.sales.entity;

import com.grainwarehouse.common.entity.BaseEntity;
import com.grainwarehouse.inventory.entity.StockDocument;
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
 * Một lần thu (hoặc hoàn) tiền của phiếu bán — BẤT BIẾN (BR-SAL-13), cùng ba lớp bảo vệ như
 * StockTransaction: không setter, @Immutable, trigger trg_sale_payment_immutable ở DB.
 * Thu nhầm thì ghi một dòng REVERSAL trỏ về dòng gốc, không sửa/xóa dòng gốc.
 */
@Getter
@Entity
@Immutable
@Table(name = "sale_payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalePayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private StockDocument document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SalePaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    // Luôn > 0; chiều (thu/hoàn) nằm ở type
    @Column(nullable = false, precision = 18, scale = 0)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    // Ghi chú khoản thu, hoặc lý do hoàn
    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private SalePayment reversalOf;

    public static SalePayment payment(StockDocument document, PaymentMethod method, BigDecimal amount,
                                      LocalDate paymentDate, String note) {
        return new SalePayment(document, SalePaymentType.PAYMENT, method, amount, paymentDate, note, null);
    }

    /** Hoàn toàn bộ khoản gốc: cùng hình thức, cùng số tiền (BR-SAL-13). */
    public static SalePayment reversalOf(SalePayment original, LocalDate date, String reason) {
        return new SalePayment(original.document, SalePaymentType.REVERSAL, original.method, original.amount,
                date, reason, original);
    }

    private SalePayment(StockDocument document, SalePaymentType type, PaymentMethod method, BigDecimal amount,
                        LocalDate paymentDate, String note, SalePayment reversalOf) {
        this.document = document;
        this.type = type;
        this.method = method;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.note = note;
        this.reversalOf = reversalOf;
    }

    /** Ảnh hưởng tới tổng đã thu của phiếu: thu cộng, hoàn trừ. */
    public BigDecimal signedAmount() {
        return type == SalePaymentType.PAYMENT ? amount : amount.negate();
    }
}
