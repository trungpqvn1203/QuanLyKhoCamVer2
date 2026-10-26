package com.grainwarehouse.cashbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một lần đặt/sửa đầu kỳ sổ quỹ — BẤT BIẾN (trigger trg_cash_book_change_immutable). Giá trị cũ NULL = lần đặt đầu tiên.
 * Đầu kỳ sửa được (là cấu hình), nhưng sửa đầu kỳ làm đổi mọi số dư → phải còn dấu vết ai sửa, lúc nào, từ bao nhiêu.
 */
@Getter
@Entity
@Immutable
@Table(name = "cash_book_change")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashBookChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "old_start_date")
    private LocalDate oldStartDate;

    @Column(name = "old_opening_cash", precision = 18, scale = 0)
    private BigDecimal oldOpeningCash;

    @Column(name = "old_opening_bank", precision = 18, scale = 0)
    private BigDecimal oldOpeningBank;

    @Column(name = "new_start_date", nullable = false)
    private LocalDate newStartDate;

    @Column(name = "new_opening_cash", nullable = false, precision = 18, scale = 0)
    private BigDecimal newOpeningCash;

    @Column(name = "new_opening_bank", nullable = false, precision = 18, scale = 0)
    private BigDecimal newOpeningBank;

    @Column(name = "changed_by", nullable = false, length = 50)
    private String changedBy;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    /** before = null: lần đặt đầu tiên. Gọi TRƯỚC khi đổi book để chụp đúng giá trị cũ. */
    public static CashBookChange of(CashBook before, LocalDate startDate, BigDecimal openingCash,
                                    BigDecimal openingBank, String changedBy) {
        CashBookChange change = new CashBookChange();
        if (before != null) {
            change.oldStartDate = before.getStartDate();
            change.oldOpeningCash = before.getOpeningCash();
            change.oldOpeningBank = before.getOpeningBank();
        }
        change.newStartDate = startDate;
        change.newOpeningCash = openingCash;
        change.newOpeningBank = openingBank;
        change.changedBy = changedBy;
        return change;
    }
}
