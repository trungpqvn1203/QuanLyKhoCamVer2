package com.grainwarehouse.payroll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ràng buộc DB của công nợ nhân viên (V18). Mỗi test rollback.
 * Mỗi test chỉ một câu lệnh sai: sau câu lỗi đầu tiên PostgreSQL bỏ cả transaction (25P02) nên câu sau không còn ý nghĩa.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeDebtPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long employeeId;
    private long payrollId;

    @BeforeEach
    void setUp() {
        employeeId = jdbcTemplate.queryForObject(
                "INSERT INTO employee (name, daily_wage) VALUES ('A', 300000) RETURNING id", Long.class);
        payrollId = jdbcTemplate.queryForObject("INSERT INTO payroll (period, status, total_amount, finalized_at)"
                + " VALUES (DATE '2026-01-01', 'FINALIZED', 600000, now()) RETURNING id", Long.class);
        jdbcTemplate.update("INSERT INTO payroll_item (payroll_id, employee_id, work_units, unload_tons, delivery_tons,"
                + " day_amount, unload_amount, delivery_amount, total_amount) VALUES (?, ?, 2, 0, 0, 600000, 0, 0, 600000)",
                payrollId, employeeId);
    }

    @Test
    @DisplayName("BR-EPAY-05: DB mặc định chốt mới có theo dõi nợ, số dư tạm ứng và số đã trả bằng 0")
    void shouldDefaultToTrackedDebtWithZeroBalances() {
        assertThat(jdbcTemplate.queryForObject("SELECT tracks_debt FROM payroll WHERE id = ?", Boolean.class, payrollId))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT advance_balance FROM employee WHERE id = ?", Long.class, employeeId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT paid_amount FROM payroll_item WHERE payroll_id = ? AND employee_id = ?", Long.class,
                payrollId, employeeId)).isZero();
    }

    @Test
    @DisplayName("BR-EPAY-03: DB chặn số dư tạm ứng âm")
    void dbShouldRejectNegativeAdvanceBalance() {
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE employee SET advance_balance = -1 WHERE id = ?", employeeId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_employee_advance_balance");
    }

    @Test
    @DisplayName("BR-EPAY-02: DB chặn trả lương vượt tổng lương của dòng")
    void dbShouldRejectPaidAboveTotal() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE payroll_item SET paid_amount = 600001 WHERE payroll_id = ? AND employee_id = ?",
                payrollId, employeeId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payroll_item_paid");
    }

    @Test
    @DisplayName("BR-EPAY-02: DB chặn số đã trả âm")
    void dbShouldRejectNegativePaid() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE payroll_item SET paid_amount = -1 WHERE payroll_id = ? AND employee_id = ?", payrollId, employeeId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payroll_item_paid");
    }

    @Test
    @DisplayName("BR-EPAY-04: DB chặn sửa dòng trả lương (trigger)")
    void dbShouldRejectUpdatingPayrollPayment() {
        long paymentId = insertPayment("CASH", "PAYMENT", 1000);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE payroll_payment SET amount = 1 WHERE id = ?", paymentId))
                .hasMessageContaining("payroll_payment là bất biến");
    }

    @Test
    @DisplayName("BR-EPAY-04: DB chặn xóa dòng trả lương (trigger)")
    void dbShouldRejectDeletingPayrollPayment() {
        long paymentId = insertPayment("CASH", "PAYMENT", 1000);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM payroll_payment WHERE id = ?", paymentId))
                .hasMessageContaining("payroll_payment là bất biến");
    }

    @Test
    @DisplayName("BR-EPAY-04: DB chặn sửa và xóa nhật ký tạm ứng (trigger)")
    void dbShouldRejectChangingAdvance() {
        long advanceId = jdbcTemplate.queryForObject("INSERT INTO employee_advance (employee_id, type, method, amount,"
                + " advance_date) VALUES (?, 'ADVANCE', 'CASH', 5000, DATE '2026-01-05') RETURNING id", Long.class, employeeId);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM employee_advance WHERE id = ?", advanceId))
                .hasMessageContaining("employee_advance là bất biến");
    }

    @Test
    @DisplayName("BR-EPAY-02: payroll_payment chấp nhận ADVANCE_DEDUCTION nhưng chặn hình thức lạ")
    void dbShouldRestrictPayrollPaymentMethod() {
        insertPayment("ADVANCE_DEDUCTION", "PAYMENT", 1000);

        assertThatThrownBy(() -> insertPayment("CHEQUE", "PAYMENT", 1000))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payroll_payment_method");
    }

    @Test
    @DisplayName("BR-EPAY-03: employee_advance không có hình thức ADVANCE_DEDUCTION (tạm ứng luôn là tiền chi ra)")
    void dbShouldRejectDeductionMethodOnAdvance() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO employee_advance (employee_id, type, method, amount,"
                + " advance_date) VALUES (?, 'ADVANCE', 'ADVANCE_DEDUCTION', 5000, DATE '2026-01-05')", employeeId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_employee_advance_method");
    }

    @Test
    @DisplayName("BR-EPAY-04: DB chặn dòng hoàn không trỏ về khoản gốc; số tiền 0")
    void dbShouldRejectReversalWithoutOriginal() {
        assertThatThrownBy(() -> insertPayment("CASH", "REVERSAL", 1000))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payroll_payment_reversal");
    }

    @Test
    @DisplayName("BR-EPAY-04: DB chặn hoàn một khoản trả hai lần")
    void dbShouldRejectDoubleReversal() {
        long original = insertPayment("CASH", "PAYMENT", 1000);
        insertReversal(original);

        assertThatThrownBy(() -> insertReversal(original))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_payroll_payment_reversal_of");
    }

    @Test
    @DisplayName("DB chặn khoản trả gắn với dòng lương không tồn tại (FK kép)")
    void dbShouldRejectPaymentForMissingItem() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO payroll_payment (payroll_id, employee_id, type, method,"
                + " amount, payment_date) VALUES (?, 999999, 'PAYMENT', 'CASH', 1000, DATE '2026-01-05')", payrollId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_payroll_payment_item");
    }

    private long insertPayment(String method, String type, long amount) {
        return jdbcTemplate.queryForObject("INSERT INTO payroll_payment (payroll_id, employee_id, type, method, amount,"
                + " payment_date) VALUES (?, ?, ?, ?, ?, DATE '2026-01-05') RETURNING id", Long.class,
                payrollId, employeeId, type, method, amount);
    }

    private void insertReversal(long originalId) {
        jdbcTemplate.update("INSERT INTO payroll_payment (payroll_id, employee_id, type, method, amount, payment_date,"
                + " reversal_of_id) VALUES (?, ?, 'REVERSAL', 'CASH', 1000, DATE '2026-01-05', ?)",
                payrollId, employeeId, originalId);
    }
}
