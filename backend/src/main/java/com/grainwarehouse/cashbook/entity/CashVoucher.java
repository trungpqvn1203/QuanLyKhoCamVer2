package com.grainwarehouse.cashbook.entity;

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
 * Phiếu thu / phiếu chi khác — BẤT BIẾN (BR-CASH-04), ba lớp bảo vệ như SalePayment: không setter, @Immutable,
 * trigger trg_cash_voucher_immutable. Ghi nhầm → phiếu hoàn (REVERSAL) cùng chiều/tài khoản/danh mục/số tiền,
 * mã = mã gốc + "-H"; trong sổ quỹ phiếu hoàn mang dấu ngược phiếu gốc.
 */
@Getter
@Entity
@Immutable
@Table(name = "cash_voucher")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashVoucher extends BaseEntity {

    public static final String REVERSAL_SUFFIX = "-H";

    @Column(nullable = false, length = 20)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashVoucherType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private CashDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private CashAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private CashCategory category;

    @Column(nullable = false, precision = 18, scale = 0)
    private BigDecimal amount;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate;

    @Column(length = 200)
    private String counterparty;

    // Ghi chú phiếu, hoặc lý do hoàn
    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private CashVoucher reversalOf;

    public static CashVoucher voucher(String code, CashDirection direction, CashAccount account, CashCategory category,
                                      BigDecimal amount, LocalDate voucherDate, String counterparty, String note) {
        CashVoucher voucher = new CashVoucher();
        voucher.code = code;
        voucher.type = CashVoucherType.VOUCHER;
        voucher.direction = direction;
        voucher.account = account;
        voucher.category = category;
        voucher.amount = amount;
        voucher.voucherDate = voucherDate;
        voucher.counterparty = counterparty;
        voucher.note = note;
        return voucher;
    }

    /** Hoàn toàn bộ phiếu gốc: cùng chiều, tài khoản, danh mục, số tiền, người nộp/nhận (BR-CASH-04). */
    public static CashVoucher reversalOf(CashVoucher original, LocalDate date, String reason) {
        CashVoucher reversal = voucher(original.code + REVERSAL_SUFFIX, original.direction, original.account,
                original.category, original.amount, date, original.counterparty, reason);
        reversal.type = CashVoucherType.REVERSAL;
        reversal.reversalOf = original;
        return reversal;
    }
}
