package com.grainwarehouse.payroll;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test API bảng lương trên DB thật; mỗi test rollback nhờ @Transactional. Dùng tháng trước (đã qua hết). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PayrollApiTest {

    private static final YearMonth MONTH = YearMonth.now(CommonSpecifications.BUSINESS_ZONE).minusMonths(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long bothId;
    private long keeperId;

    @BeforeEach
    void setUp() throws Exception {
        // Hưởng cả hai cách: 1,5 công × 300.000 = 450.000; 13,625 tấn × 52.500 = 715.312,5 → 715.313
        bothId = createEmployee("""
                {"name": "Nguyễn Văn Bốc", "dailyWage": 300000, "tonRate": 52500}
                """);
        keeperId = createEmployee("""
                {"name": "Trần Thủ Kho", "dailyWage": 350000}
                """);
        record(bothId, MONTH.atDay(1), "{\"status\": \"FULL\", \"tons\": 10.125}");
        record(bothId, MONTH.atDay(2), "{\"status\": \"HALF\", \"tons\": 3.5}");
        record(keeperId, MONTH.atDay(1), "{\"status\": \"FULL\", \"tons\": 2}");
    }

    @Test
    @DisplayName("BR-PAY-01: tạm tính - mỗi phần làm tròn HALF_UP về đồng, tổng = tổng hai phần")
    void shouldPreviewWithRounding() throws Exception {
        sheet()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalized").value(false))
                .andExpect(jsonPath("$.data.payrollId").doesNotExist())
                .andExpect(line(bothId, "workUnits").value(1.5))
                .andExpect(line(bothId, "tons").value(13.625))
                .andExpect(line(bothId, "dayAmount").value(450000))
                .andExpect(line(bothId, "tonAmount").value(715313))
                .andExpect(line(bothId, "totalAmount").value(1165313))
                // Thủ kho có 2 tấn nhưng không có đơn giá tấn → tiền bốc vác 0 (BR-EMP-02)
                .andExpect(line(keeperId, "tonAmount").value(0))
                .andExpect(line(keeperId, "totalAmount").value(350000))
                .andExpect(jsonPath("$.data.totalAmount").value(1515313));
    }

    @Test
    @DisplayName("BR-PAY-02: chốt rồi đổi đơn giá nhân viên - bảng lương đã chốt giữ đơn giá lúc chốt")
    void finalizedPayrollShouldKeepSnapshotRates() throws Exception {
        finalizeMonth().andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalized").value(true))
                .andExpect(jsonPath("$.data.payrollId").exists());

        mockMvc.perform(put("/api/employees/{id}", bothId).contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Nguyễn Văn Bốc", "dailyWage": 999999, "tonRate": 99999, "active": true}
                """)).andExpect(status().isOk());

        sheet()
                .andExpect(line(bothId, "dailyWage").value(300000))
                .andExpect(line(bothId, "tonRate").value(52500))
                .andExpect(line(bothId, "totalAmount").value(1165313));
    }

    @Test
    @DisplayName("BR-PAY-03: tháng đã chốt - chấm công, xóa ô bị chặn 409; bảng công báo payrollFinalized")
    void finalizedMonthShouldLockAttendance() throws Exception {
        finalizeMonth().andExpect(status().isOk());

        record(bothId, MONTH.atDay(3), "{\"status\": \"FULL\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        mockMvc.perform(delete("/api/attendance/{employeeId}/{date}", bothId, MONTH.atDay(1)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/attendance").param("month", MONTH.toString()))
                .andExpect(jsonPath("$.data.payrollFinalized").value(true));
        // Tháng khác không bị khóa
        record(bothId, MONTH.minusMonths(1).atDay(3), "{\"status\": \"FULL\"}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("BR-PAY-03: chốt hai lần - 409; hủy chốt không lý do - 400")
    void shouldRejectDoubleFinalizeAndCancelWithoutReason() throws Exception {
        finalizeMonth().andExpect(status().isOk());
        finalizeMonth().andExpect(status().isConflict());

        cancel("{\"reason\": \"  \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("reason"));
    }

    @Test
    @DisplayName("BR-PAY-03: hủy chốt → bản cũ vào lịch sử, mở khóa chấm công, sửa xong chốt lại với số mới")
    void cancelShouldKeepHistoryAndReopenMonth() throws Exception {
        finalizeMonth();

        cancel("{\"reason\": \"Quên chấm ngày 3\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalized").value(false))
                .andExpect(jsonPath("$.data.history.length()").value(1))
                .andExpect(jsonPath("$.data.history[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.history[0].cancelReason").value("Quên chấm ngày 3"))
                .andExpect(jsonPath("$.data.history[0].totalAmount").value(1515313));

        record(keeperId, MONTH.atDay(3), "{\"status\": \"FULL\"}").andExpect(status().isOk());

        finalizeMonth()
                .andExpect(jsonPath("$.data.finalized").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(1865313))
                .andExpect(jsonPath("$.data.history.length()").value(1));
    }

    @Test
    @DisplayName("Hủy khi chưa chốt - 409; chốt tháng không có chấm công - 409; tháng tương lai - 400")
    void shouldRejectInvalidTransitions() throws Exception {
        cancel("{\"reason\": \"x\"}").andExpect(status().isConflict());

        mockMvc.perform(post("/api/payrolls/{month}/finalize", MONTH.minusMonths(6)))
                .andExpect(status().isConflict());

        YearMonth nextMonth = YearMonth.now(CommonSpecifications.BUSINESS_ZONE).plusMonths(1);
        mockMvc.perform(post("/api/payrolls/{month}/finalize", nextMonth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("month"));
    }

    @Test
    @DisplayName("Nhân viên đã nghỉ nhưng có công trong tháng vẫn được tính lương")
    void inactiveEmployeeWithAttendanceShouldBePaid() throws Exception {
        mockMvc.perform(delete("/api/employees/{id}", keeperId)).andExpect(status().isNoContent());

        finalizeMonth()
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(line(keeperId, "employeeActive").value(false))
                .andExpect(line(keeperId, "totalAmount").value(350000));
    }

    private org.springframework.test.web.servlet.result.JsonPathResultMatchers line(long employeeId, String field) {
        return jsonPath("$.data.items[?(@.employeeId == %d)].%s", employeeId, field);
    }

    private ResultActions sheet() throws Exception {
        return mockMvc.perform(get("/api/payrolls/{month}", MONTH));
    }

    private ResultActions finalizeMonth() throws Exception {
        return mockMvc.perform(post("/api/payrolls/{month}/finalize", MONTH));
    }

    private ResultActions cancel(String json) throws Exception {
        return mockMvc.perform(post("/api/payrolls/{month}/cancel", MONTH)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions record(long employeeId, LocalDate date, String json) throws Exception {
        return mockMvc.perform(put("/api/attendance/{employeeId}/{date}", employeeId, date)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createEmployee(String json) {
        try {
            String body = mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andReturn().getResponse().getContentAsString();
            return com.jayway.jsonpath.JsonPath.<Number>read(body, "$.data.id").longValue();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
