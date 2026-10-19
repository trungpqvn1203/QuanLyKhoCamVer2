package com.grainwarehouse.attendance;

import com.grainwarehouse.common.search.CommonSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test API chấm công trên DB thật; mỗi test rollback nhờ @Transactional.
 * Dùng tháng TRƯỚC (luôn đủ ngày trong quá khứ) để test không phụ thuộc hôm nay là ngày mấy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttendanceApiTest {

    private static final YearMonth LAST_MONTH = YearMonth.now(CommonSpecifications.BUSINESS_ZONE).minusMonths(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long loaderId;
    private long keeperId;

    @BeforeEach
    void setUp() {
        loaderId = insertEmployee("Nguyễn Văn Bốc", null, "52500");
        keeperId = insertEmployee("Trần Thủ Kho", "300000", null);
    }

    @Test
    @DisplayName("BR-ATT-01: PUT ô mới - tạo; PUT lại cùng ô - ghi đè, không sinh dòng thứ hai")
    void shouldCreateThenOverwriteCell() throws Exception {
        LocalDate day = LAST_MONTH.atDay(3);

        record(loaderId, day, """
                {"status": "FULL", "tons": 12.5}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FULL"))
                .andExpect(jsonPath("$.data.tons").value(12.5));

        record(loaderId, day, """
                {"status": "HALF", "tons": 4.25, "note": "Chiều nghỉ"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("HALF"))
                .andExpect(jsonPath("$.data.note").value("Chiều nghỉ"));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM attendance WHERE employee_id = ?", Integer.class, loaderId);
        org.assertj.core.api.Assertions.assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-ATT-01: bảng công tháng - mọi nhân viên đang làm, tổng công (1 + 0,5) và tổng tấn")
    void shouldBuildMonthlySheetWithTotals() throws Exception {
        record(loaderId, LAST_MONTH.atDay(1), "{\"status\": \"FULL\", \"tons\": 10.125}");
        record(loaderId, LAST_MONTH.atDay(2), "{\"status\": \"HALF\", \"tons\": 3.5}");
        record(loaderId, LAST_MONTH.atDay(3), "{\"status\": \"ABSENT\"}");
        record(keeperId, LAST_MONTH.atDay(1), "{\"status\": \"FULL\"}");

        mockMvc.perform(get("/api/attendance").param("month", LAST_MONTH.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value(LAST_MONTH.toString()))
                .andExpect(jsonPath("$.data.daysInMonth").value(LAST_MONTH.lengthOfMonth()))
                .andExpect(jsonPath("$.data.employees.length()").value(2))
                .andExpect(jsonPath("$.data.employees[?(@.employeeId == %d)].totalWorkUnits", loaderId).value(1.5))
                .andExpect(jsonPath("$.data.employees[?(@.employeeId == %d)].totalTons", loaderId).value(13.625))
                .andExpect(jsonPath("$.data.employees[?(@.employeeId == %d)].days.length()", loaderId).value(3))
                .andExpect(jsonPath("$.data.employees[?(@.employeeId == %d)].paysByTon", loaderId).value(true))
                .andExpect(jsonPath("$.data.employees[?(@.employeeId == %d)].paysByDay", loaderId).value(false))
                .andExpect(jsonPath("$.data.employees[?(@.employeeId == %d)].totalWorkUnits", keeperId).value(1));
    }

    @Test
    @DisplayName("Nhân viên đã nghỉ vẫn hiện ở tháng có chấm công của họ, không hiện ở tháng khác")
    void shouldShowInactiveEmployeeOnlyInMonthsWithRecords() throws Exception {
        record(keeperId, LAST_MONTH.atDay(5), "{\"status\": \"FULL\"}");
        // Qua API chứ không UPDATE thẳng: test chạy chung một transaction, Hibernate còn giữ bản active=true
        // trong bộ nhớ đệm (persistence context) nên không thấy câu UPDATE viết tay
        mockMvc.perform(delete("/api/employees/{id}", keeperId)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/attendance").param("month", LAST_MONTH.toString()))
                .andExpect(jsonPath("$.data.employees.length()").value(2))
                .andExpect(jsonPath("$.data.employees[?(@.employeeId == %d)].active", keeperId).value(false));

        mockMvc.perform(get("/api/attendance").param("month", LAST_MONTH.minusMonths(1).toString()))
                .andExpect(jsonPath("$.data.employees.length()").value(1));
    }

    @Test
    @DisplayName("BR-ATT-02: ngày nghỉ mà có tấn - 400 field tons")
    void shouldRejectTonsOnAbsentDay() throws Exception {
        record(loaderId, LAST_MONTH.atDay(4), "{\"status\": \"ABSENT\", \"tons\": 2}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("tons"));
    }

    @Test
    @DisplayName("BR-ATT-02: ngày tương lai (giờ VN) - 400 field date")
    void shouldRejectFutureDate() throws Exception {
        LocalDate tomorrow = LocalDate.now(CommonSpecifications.BUSINESS_ZONE).plusDays(1);

        record(loaderId, tomorrow, "{\"status\": \"FULL\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("date"));
    }

    @Test
    @DisplayName("BR-ATT-02: nhân viên đã nghỉ - 409 BUSINESS_RULE_VIOLATION")
    void shouldRejectInactiveEmployee() throws Exception {
        jdbcTemplate.update("UPDATE employee SET active = false WHERE id = ?", loaderId);

        record(loaderId, LAST_MONTH.atDay(1), "{\"status\": \"FULL\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("Thiếu trạng thái, tấn âm, tấn quá 3 chữ số lẻ - 400")
    void shouldRejectInvalidBody() throws Exception {
        record(loaderId, LAST_MONTH.atDay(1), "{\"tons\": -1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        record(loaderId, LAST_MONTH.atDay(1), "{\"status\": \"FULL\", \"tons\": 1.2345}")
                .andExpect(status().isBadRequest());
        record(loaderId, LAST_MONTH.atDay(1), "{\"status\": \"OVERTIME\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("DELETE ô - 204, ngày trở về chưa chấm; xóa ô chưa chấm - 404")
    void shouldClearCell() throws Exception {
        LocalDate day = LAST_MONTH.atDay(6);
        record(loaderId, day, "{\"status\": \"FULL\", \"tons\": 3}");

        mockMvc.perform(delete("/api/attendance/{employeeId}/{date}", loaderId, day))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/attendance/{employeeId}/{date}", loaderId, day))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tháng sai định dạng, nhân viên không tồn tại - 400 / 404")
    void shouldRejectBadMonthAndUnknownEmployee() throws Exception {
        mockMvc.perform(get("/api/attendance").param("month", "2026-13"))
                .andExpect(status().isBadRequest());
        record(999_999L, LAST_MONTH.atDay(1), "{\"status\": \"FULL\"}")
                .andExpect(status().isNotFound());
    }

    private ResultActions record(long employeeId, LocalDate date, String json) throws Exception {
        return mockMvc.perform(put("/api/attendance/{employeeId}/{date}", employeeId, date)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private long insertEmployee(String name, String dailyWage, String tonRate) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO employee (name, daily_wage, ton_rate) VALUES (?, ?::numeric, ?::numeric) RETURNING id",
                Long.class, name, dailyWage, tonRate);
    }
}
