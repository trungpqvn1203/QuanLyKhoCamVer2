package com.grainwarehouse.employee;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ràng buộc ở tầng DATABASE (migration V9), INSERT thẳng qua JdbcTemplate.
 * Mỗi vi phạm một test: sau lỗi đầu tiên PostgreSQL hủy cả transaction (xem LESSONS, module Khách hàng).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeePersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("BR-EMP-01: DB tự sinh mã NV + 3 chữ số; có một đơn giá là đủ")
    void shouldGenerateCode() {
        String code = jdbcTemplate.queryForObject(
                "INSERT INTO employee (name, ton_rate) VALUES ('A', 50000) RETURNING code", String.class);

        assertThat(code).matches("NV\\d{3,}");
    }

    @Test
    @DisplayName("BR-EMP-02: DB chặn nhân viên không có đơn giá nào")
    void dbShouldRejectEmployeeWithoutRate() {
        assertThatThrownBy(() -> insertEmployee(null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_employee_has_rate");
    }

    @Test
    @DisplayName("BR-EMP-02: DB chặn đơn giá âm")
    void dbShouldRejectNegativeRate() {
        assertThatThrownBy(() -> insertEmployee(null, new BigDecimal("-1"), new BigDecimal("50000")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_employee_rates");
    }

    @Test
    @DisplayName("BR-EMP-03: DB chặn SĐT chưa chuẩn hóa")
    void dbShouldRejectUnnormalizedPhone() {
        assertThatThrownBy(() -> insertEmployee("0901 234 567", null, new BigDecimal("50000")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_employee_phone");
    }

    private void insertEmployee(String phone, BigDecimal dailyWage, BigDecimal tonRate) {
        jdbcTemplate.update("INSERT INTO employee (name, phone, daily_wage, ton_rate) VALUES ('A', ?, ?, ?)",
                phone, dailyWage, tonRate);
    }
}
