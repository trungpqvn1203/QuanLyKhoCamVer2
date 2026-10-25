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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ràng buộc DB của V12, INSERT thẳng. Mỗi vi phạm một test (lỗi đầu tiên hủy cả transaction PostgreSQL). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PayrollPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long employeeId;

    @BeforeEach
    void setUp() {
        employeeId = jdbcTemplate.queryForObject(
                "INSERT INTO employee (name, unload_ton_rate) VALUES ('A', 50000) RETURNING id", Long.class);
    }

    @Test
    @DisplayName("BR-PAY-03: DB chặn hai bảng lương CÒN HIỆU LỰC cùng tháng; bản đã hủy không tính")
    void dbShouldAllowOnlyOneFinalizedPerMonth() {
        insertCancelled("2026-01-01");
        insertFinalized("2026-01-01");

        assertThatThrownBy(() -> insertFinalized("2026-01-01"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_payroll_finalized_period");
    }

    @Test
    @DisplayName("DB chặn kỳ lương không phải ngày 1")
    void dbShouldRejectPeriodNotFirstDay() {
        assertThatThrownBy(() -> insertFinalized("2026-01-15"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payroll_period_first_day");
    }

    @Test
    @DisplayName("BR-PAY-03: DB chặn bản hủy không có lý do")
    void dbShouldRejectCancelWithoutReason() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO payroll (period, status, total_amount, finalized_at,"
                + " cancelled_at) VALUES (DATE '2026-01-01', 'CANCELLED', 0, now(), now())"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payroll_cancel");
    }

    @Test
    @DisplayName("BR-PAY-01: DB chặn dòng lương có tổng khác tiền công + tiền bốc xuống + tiền giao hàng")
    void dbShouldRejectItemWithWrongTotal() {
        long payrollId = insertFinalized("2026-01-01");

        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO payroll_item (payroll_id, employee_id, work_units,"
                + " unload_tons, delivery_tons, day_amount, unload_amount, delivery_amount, total_amount)"
                + " VALUES (?, ?, 1, 1, 1, 100, 200, 50, 351)", payrollId, employeeId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payroll_item_total");
    }

    private long insertFinalized(String period) {
        return jdbcTemplate.queryForObject("INSERT INTO payroll (period, status, total_amount, finalized_at)"
                + " VALUES (?::date, 'FINALIZED', 0, now()) RETURNING id", Long.class, period);
    }

    private void insertCancelled(String period) {
        jdbcTemplate.update("INSERT INTO payroll (period, status, total_amount, finalized_at, cancel_reason, cancelled_at)"
                + " VALUES (?::date, 'CANCELLED', 0, now(), 'test', now())", period);
    }
}
