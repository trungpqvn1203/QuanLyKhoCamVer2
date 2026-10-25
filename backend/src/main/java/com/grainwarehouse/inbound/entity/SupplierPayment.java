package com.grainwarehouse.inbound.entity;

import com.grainwarehouse.common.entity.BaseEntity;
import com.grainwarehouse.inventory.entity.StockDocument;
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
 * Một lần trả (hoặc hoàn) tiền cho NCC trên một phiếu nhập — BẤT BIẾN (BR-SPAY-03), cùng ba lớp bảo vệ như
 * SalePayment: không setter, @Immutable, trigger trg_supplier_payment_immutable ở DB.
 * Hình thức (PaymentMethod) dùng chung với thu tiền khách: cùng hai giá trị tiền mặt / chuyển khoản.
 */
@Getter
@Entity
@Immutable
@Table(name = "supplier_payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private StockDocument document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SupplierPaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

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
    private SupplierPayment reversalOf;

    public static SupplierPayment payment(StockDocument document, PaymentMethod method, BigDecimal amount,
                                          LocalDate paymentDate, String note) {
        return new SupplierPayment(document, SupplierPaymentType.PAYMENT, method, amount, paymentDate, note, null);
    }

    /** Hoàn toàn bộ khoản gốc: cùng hình thức, cùng số tiền (BR-SPAY-03). */
    public static SupplierPayment reversalOf(SupplierPayment original, LocalDate date, String reason) {
        return new SupplierPayment(original.document, SupplierPaymentType.REVERSAL, original.method, original.amount,
                date, reason, original);
    }

    private SupplierPayment(StockDocument document, SupplierPaymentType type, PaymentMethod method,
                            BigDecimal amount, LocalDate paymentDate, String note, SupplierPayment reversalOf) {
        this.document = document;
        this.type = type;
        this.method = method;
        this.amount = amount;
        this.paymentDate = paymentDate;
        this.note = note;
        this.reversalOf = reversalOf;
    }

    /** Ảnh hưởng tới tổng đã trả của phiếu: trả cộng, hoàn trừ. */
    public BigDecimal signedAmount() {
        return type == SupplierPaymentType.PAYMENT ? amount : amount.negate();
    }
}
