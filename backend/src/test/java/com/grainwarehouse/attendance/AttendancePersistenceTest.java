package com.grainwarehouse.attendance;

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

/** Ràng buộc DB của V11, INSERT thẳng. Mỗi vi phạm một test (lỗi đầu tiên hủy cả transaction PostgreSQL). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AttendancePersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long employeeId;

    @BeforeEach
    void setUp() {
        employeeId = jdbcTemplate.queryForObject(
                "INSERT INTO employee (name, ton_rate) VALUES ('A', 50000) RETURNING id", Long.class);
    }

    @Test
    @DisplayName("BR-ATT-01: DB chặn hai dòng cùng nhân viên, cùng ngày")
    void dbShouldRejectDuplicateCell() {
        insert("FULL", "1");

        assertThatThrownBy(() -> insert("HALF", "0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_attendance_employee_date");
    }

    @Test
    @DisplayName("BR-ATT-02: DB chặn ngày nghỉ có tấn")
    void dbShouldRejectTonsOnAbsentDay() {
        assertThatThrownBy(() -> insert("ABSENT", "2"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_attendance_absent_no_tons");
    }

    @Test
    @DisplayName("BR-ATT-01: DB chặn tấn âm")
    void dbShouldRejectNegativeTons() {
        assertThatThrownBy(() -> insert("FULL", "-1"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_attendance_tons");
    }

    @Test
    @DisplayName("BR-ATT-01: DB chặn trạng thái lạ")
    void dbShouldRejectUnknownStatus() {
        assertThatThrownBy(() -> insert("OVERTIME", "0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_attendance_status");
    }

    private void insert(String status, String tons) {
        jdbcTemplate.update("INSERT INTO attendance (employee_id, work_date, day_status, tons)"
                + " VALUES (?, DATE '2026-01-05', ?, ?::numeric)", employeeId, status, tons);
    }
}
