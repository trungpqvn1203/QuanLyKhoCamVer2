package com.grainwarehouse.cashbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ràng buộc DB của sổ quỹ (V19). Mỗi test rollback; mỗi test chỉ một câu lệnh sai
 * (sau câu lỗi đầu tiên PostgreSQL bỏ cả transaction — 25P02).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CashBookPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categoryId;

    @BeforeEach
    void setUp() {
        categoryId = jdbcTemplate.queryForObject(
                "INSERT INTO cash_category (name, direction) VALUES ('Xăng xe test', 'OUT') RETURNING id", Long.class);
    }

    @Test
    @DisplayName("BR-CASH-03: DB chỉ cho đúng một dòng đầu kỳ (id = 1)")
    void dbShouldAllowSingleCashBookRow() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO cash_book (id, start_date, opening_cash, opening_bank) VALUES (2, DATE '2026-01-01', 0, 0)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_cash_book_single_row");
    }

    @Test
    @DisplayName("BR-CASH-03: DB chặn số dư đầu kỳ âm")
    void dbShouldRejectNegativeOpening() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO cash_book (id, start_date, opening_cash, opening_bank) VALUES (1, DATE '2026-01-01', -1, 0)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_cash_book_opening");
    }

    @Test
    @DisplayName("BR-CASH-04: DB chặn sửa phiếu thu/chi (trigger)")
    void dbShouldRejectUpdatingVoucher() {
        long id = insertVoucher("TXPC1", 1000);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE cash_voucher SET amount = 1 WHERE id = ?", id))
                .hasMessageContaining("cash_voucher là bất biến");
    }

    @Test
    @DisplayName("BR-CASH-04: DB chặn xóa phiếu thu/chi (trigger)")
    void dbShouldRejectDeletingVoucher() {
        long id = insertVoucher("TXPC1", 1000);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM cash_voucher WHERE id = ?", id))
                .hasMessageContaining("cash_voucher là bất biến");
    }

    @Test
    @DisplayName("BR-CASH-03: DB chặn sửa lịch sử đầu kỳ (trigger)")
    void dbShouldRejectChangingOpeningHistory() {
        long id = jdbcTemplate.queryForObject("INSERT INTO cash_book_change (new_start_date, new_opening_cash,"
                + " new_opening_bank, changed_by) VALUES (DATE '2026-01-01', 0, 0, 'a') RETURNING id", Long.class);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM cash_book_change WHERE id = ?", id))
                .hasMessageContaining("cash_book_change là bất biến");
    }

    @Test
    @DisplayName("BR-CASH-04: DB chặn phiếu số tiền 0")
    void dbShouldRejectZeroAmount() {
        assertThatThrownBy(() -> insertVoucher("TXPC1", 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_cash_voucher_amount");
    }

    @Test
    @DisplayName("BR-CASH-04: DB chặn trùng mã phiếu")
    void dbShouldRejectDuplicateCode() {
        insertVoucher("TXPC1", 1000);

        assertThatThrownBy(() -> insertVoucher("TXPC1", 2000))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_cash_voucher_code");
    }

    @Test
    @DisplayName("BR-CASH-01: DB chặn tài khoản lạ")
    void dbShouldRejectUnknownAccount() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO cash_voucher (code, type, direction, account,"
                + " category_id, amount, voucher_date) VALUES ('TXPC2', 'VOUCHER', 'OUT', 'CARD', ?, 1000, DATE '2026-01-05')",
                categoryId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_cash_voucher_account");
    }

    private long insertVoucher(String code, long amount) {
        return jdbcTemplate.queryForObject("INSERT INTO cash_voucher (code, type, direction, account, category_id,"
                + " amount, voucher_date) VALUES (?, 'VOUCHER', 'OUT', 'CASH', ?, ?, DATE '2026-01-05') RETURNING id",
                Long.class, code, categoryId, amount);
    }
}
