package com.grainwarehouse.cashbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Đầu kỳ sổ quỹ (BR-CASH-03): ngày bắt đầu + số dư tiền mặt và tài khoản tại ngày đó. Đúng một dòng (id = 1,
 * CHECK ck_cash_book_single_row). Là cấu hình nên sửa được; mọi lần đặt/sửa ghi vào CashBookChange.
 */
@Getter
@Entity
@Table(name = "cash_book")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashBook {

    public static final short ID = 1;

    @Id
    private Short id;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "opening_cash", nullable = false, precision = 18, scale = 0)
    private BigDecimal openingCash;

    @Column(name = "opening_bank", nullable = false, precision = 18, scale = 0)
    private BigDecimal openingBank;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static CashBook create(LocalDate startDate, BigDecimal openingCash, BigDecimal openingBank) {
        CashBook book = new CashBook();
        book.id = ID;
        book.update(startDate, openingCash, openingBank);
        return book;
    }

    public void update(LocalDate startDate, BigDecimal openingCash, BigDecimal openingBank) {
        this.startDate = startDate;
        this.openingCash = openingCash;
        this.openingBank = openingBank;
    }

    public BigDecimal opening(CashAccount account) {
        return account == CashAccount.CASH ? openingCash : openingBank;
    }
}
