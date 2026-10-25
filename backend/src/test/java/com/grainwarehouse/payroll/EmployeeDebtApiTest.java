package com.grainwarehouse.payroll;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grainwarehouse.common.search.CommonSpecifications;
import jakarta.persistence.EntityManager;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trả lương theo bảng lương tháng, tạm ứng và công nợ nhân viên (BR-EPAY-01..06) trên DB thật; mỗi test rollback.
 * Bộ dữ liệu: tháng TRƯỚC (luôn đủ ngày trong quá khứ). Bốc: 2 công × 300.000 = 600.000; Thủ kho: 1 công × 350.000.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeeDebtApiTest {

    private static final YearMonth MONTH = YearMonth.now(CommonSpecifications.BUSINESS_ZONE).minusMonths(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private long bocId;
    private long keeperId;
    private long idleId;

    @BeforeEach
    void setUp() throws Exception {
        bocId = createEmployee("{\"name\": \"Nguyễn Văn Bốc\", \"dailyWage\": 300000}");
        keeperId = createEmployee("{\"name\": \"Trần Thủ Kho\", \"dailyWage\": 350000}");
        idleId = createEmployee("{\"name\": \"Lê Chưa Làm\", \"dailyWage\": 280000}");
        record(bocId, MONTH.atDay(1));
        record(bocId, MONTH.atDay(2));
        record(keeperId, MONTH.atDay(1));
    }

    // ===== Trả lương =====

    @Test
    @DisplayName("BR-EPAY-02: trả lương bằng tiền mặt + chuyển khoản + trừ tạm ứng - 201, ba dòng, đã trả/còn nợ/số dư tạm ứng cập nhật")
    void shouldPayWithCashTransferAndAdvanceDeduction() throws Exception {
        finalizeMonth();
        advance(bocId, Map.of("cashAmount", 200000));

        pay(bocId, Map.of("cashAmount", 300000, "transferAmount", 100000, "advanceDeduction", 150000, "note", "Đợt 1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalAmount").value(600000))
                .andExpect(jsonPath("$.data.paidAmount").value(550000))
                .andExpect(jsonPath("$.data.outstandingAmount").value(50000))
                .andExpect(jsonPath("$.data.advanceBalance").value(50000))
                .andExpect(jsonPath("$.data.tracksDebt").value(true))
                .andExpect(jsonPath("$.data.payments.length()").value(3))
                .andExpect(jsonPath("$.data.payments[0].method").value("CASH"))
                .andExpect(jsonPath("$.data.payments[1].method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.data.payments[2].method").value("ADVANCE_DEDUCTION"))
                .andExpect(jsonPath("$.data.payments[0].paymentDate").value(today().toString()))
                .andExpect(jsonPath("$.data.payments[2].note").value("Đợt 1"));

        // Trả nốt đúng bằng số còn nợ: được; bảng lương hiện đã trả / còn nợ của từng người
        pay(bocId, Map.of("transferAmount", 50000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.outstandingAmount").value(0));
        sheet()
                .andExpect(line(bocId, "paidAmount").value(600000))
                .andExpect(line(bocId, "outstandingAmount").value(0))
                .andExpect(line(keeperId, "paidAmount").value(0))
                .andExpect(line(keeperId, "outstandingAmount").value(350000));
        assertAdvanceBalanceMatchesLedger(bocId);
    }

    @Test
    @DisplayName("BR-EPAY-02, 03: trả vượt số lương còn nợ / trừ vượt số dư tạm ứng - 409, không ghi dòng nào")
    void shouldRejectOverpaymentAndOverDeduction() throws Exception {
        finalizeMonth();
        advance(bocId, Map.of("cashAmount", 100000));

        pay(bocId, Map.of("cashAmount", 500000, "transferAmount", 100001))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("vượt số lương còn nợ 600.000 đ")));
        pay(bocId, Map.of("advanceDeduction", 100001))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("vượt số dư tạm ứng hiện có 100.000 đ")));

        assertThat(paymentRows()).isZero();
        assertThat(advanceBalance(bocId)).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("BR-EPAY-02: số tiền 0 / âm / lẻ đồng - 400; ngày trả chọn tự do")
    void shouldValidateAmountsAndAcceptAnyDate() throws Exception {
        finalizeMonth();

        pay(bocId, Map.of("cashAmount", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("cashAmount"));
        pay(bocId, Map.of("transferAmount", -1)).andExpect(status().isBadRequest());
        pay(bocId, Map.of("advanceDeduction", 10.5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("advanceDeduction"));

        LocalDate future = today().plusDays(5);
        pay(bocId, Map.of("cashAmount", 1000, "paymentDate", future.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payments[0].paymentDate").value(future.toString()));
    }

    @Test
    @DisplayName("BR-EPAY-01: tháng chưa chốt - chưa trả lương được (409); nhân viên không có lương tháng đó - 404")
    void shouldRejectPaymentBeforeFinalizeOrWithoutPayrollLine() throws Exception {
        pay(bocId, Map.of("cashAmount", 1000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("chưa chốt lương")));

        finalizeMonth();
        pay(idleId, Map.of("cashAmount", 1000)).andExpect(status().isNotFound());
        pay(999_999L, Map.of("cashAmount", 1000)).andExpect(status().isNotFound());
    }

    // ===== Hoàn khoản trả lương =====

    @Test
    @DisplayName("BR-EPAY-04: hoàn khoản trả - REVERSAL trỏ về gốc; hoàn khoản trừ tạm ứng thì số dư tạm ứng cộng lại; hoàn lần 2 / hoàn dòng hoàn - 409")
    void shouldReversePayments() throws Exception {
        finalizeMonth();
        advance(bocId, Map.of("cashAmount", 200000));
        JsonNode paid = payOk(bocId, Map.of("cashAmount", 100000, "advanceDeduction", 150000));
        long cashId = paid.get("payments").get(0).get("id").asLong();
        long deductionId = paid.get("payments").get(1).get("id").asLong();
        assertThat(advanceBalance(bocId)).isEqualByComparingTo("50000");

        reversePayment(bocId, deductionId, " ").andExpect(status().isBadRequest());
        reversePayment(bocId, deductionId, "Trừ nhầm")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAmount").value(100000))
                .andExpect(jsonPath("$.data.outstandingAmount").value(500000))
                .andExpect(jsonPath("$.data.advanceBalance").value(200000))
                .andExpect(jsonPath("$.data.payments[1].reversed").value(true))
                .andExpect(jsonPath("$.data.payments[2].type").value("REVERSAL"))
                .andExpect(jsonPath("$.data.payments[2].reversalOfId").value(deductionId))
                .andExpect(jsonPath("$.data.payments[2].method").value("ADVANCE_DEDUCTION"))
                .andExpect(jsonPath("$.data.payments[2].note").value("Trừ nhầm"));

        reversePayment(bocId, cashId, "Chi nhầm người")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAmount").value(0));

        reversePayment(bocId, deductionId, "Lần 2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("đã được hoàn")));
        long reversalId = paymentIds(bocId)[2];
        reversePayment(bocId, reversalId, "Hoàn dòng hoàn").andExpect(status().isConflict());
        assertAdvanceBalanceMatchesLedger(bocId);
    }

    @Test
    @DisplayName("Khoản trả của nhân viên khác - 404")
    void shouldNotTouchPaymentOfAnotherEmployee() throws Exception {
        finalizeMonth();
        long paymentId = payOk(bocId, Map.of("cashAmount", 1000)).get("payments").get(0).get("id").asLong();

        reversePayment(keeperId, paymentId, "Nhầm người").andExpect(status().isNotFound());
    }

    // ===== Hủy chốt, bảng lương cũ =====

    @Test
    @DisplayName("BR-EPAY-05: đã trả lương (dù chỉ một người) - không hủy chốt được; hoàn hết khoản trả rồi mới hủy được")
    void cancelShouldBeBlockedWhileAnyPaid() throws Exception {
        finalizeMonth();
        long paymentId = payOk(keeperId, Map.of("cashAmount", 50000)).get("payments").get(0).get("id").asLong();

        cancel("{\"reason\": \"Quên chấm ngày 3\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("đã trả lương cho 1 nhân viên")));
        sheet().andExpect(jsonPath("$.data.finalized").value(true));

        reversePayment(keeperId, paymentId, "Trả nhầm").andExpect(status().isOk());
        cancel("{\"reason\": \"Quên chấm ngày 3\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalized").value(false));
    }

    @Test
    @DisplayName("BR-EPAY-05: bảng lương chốt trước module (tracks_debt = false) - còn nợ 0, không trả lương được, không vào công nợ")
    void legacyPayrollShouldBeTreatedAsPaid() throws Exception {
        finalizeMonth();
        markLegacy();

        sheet()
                .andExpect(jsonPath("$.data.tracksDebt").value(false))
                .andExpect(line(bocId, "outstandingAmount").value(0));
        pay(bocId, Map.of("cashAmount", 1000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("được coi là đã trả đủ")));
        mockMvc.perform(get("/api/employee-debts")).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/payrolls/{month}/items/{id}", MONTH, bocId))
                .andExpect(jsonPath("$.data.tracksDebt").value(false))
                .andExpect(jsonPath("$.data.outstandingAmount").value(0));
    }

    // ===== Tạm ứng =====

    @Test
    @DisplayName("BR-EPAY-03: ứng tiền mặt + chuyển khoản - 201, số dư tạm ứng cộng dồn; số tiền 0 / lẻ - 400")
    void shouldRecordAdvanceAndAccumulateBalance() throws Exception {
        advanceRequest(bocId, Map.of("cashAmount", 300000, "transferAmount", 200000, "note", "Ứng đầu tháng"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.advanceBalance").value(500000))
                .andExpect(jsonPath("$.data.advances.length()").value(2))
                .andExpect(jsonPath("$.data.advances[0].type").value("ADVANCE"))
                .andExpect(jsonPath("$.data.advances[0].advanceDate").value(today().toString()));
        advanceRequest(bocId, Map.of("cashAmount", 100000))
                .andExpect(jsonPath("$.data.advanceBalance").value(600000));

        advanceRequest(bocId, Map.of("cashAmount", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("cashAmount"));
        advanceRequest(bocId, Map.of("transferAmount", 10.5)).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/employees/{id}/advances", bocId))
                .andExpect(jsonPath("$.data.advanceBalance").value(600000));
        assertAdvanceBalanceMatchesLedger(bocId);
    }

    @Test
    @DisplayName("BR-EPAY-03: không ứng cho nhân viên đã nghỉ việc (409); nhân viên không tồn tại (404)")
    void shouldRejectAdvanceForInactiveOrUnknownEmployee() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/employees/{id}", idleId)).andExpect(status().isNoContent());

        advanceRequest(idleId, Map.of("cashAmount", 1000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("đã nghỉ việc")));
        advanceRequest(999_999L, Map.of("cashAmount", 1000)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-EPAY-04: hoàn khoản ứng - số dư giảm, hoàn lần 2 / hoàn dòng hoàn - 409; khoản ứng của người khác - 404")
    void shouldReverseAdvance() throws Exception {
        JsonNode ledger = advance(bocId, Map.of("cashAmount", 200000));
        long advanceId = ledger.get("advances").get(0).get("id").asLong();

        reverseAdvance(keeperId, advanceId, "Nhầm người").andExpect(status().isNotFound());
        reverseAdvance(bocId, advanceId, " ").andExpect(status().isBadRequest());
        reverseAdvance(bocId, advanceId, "Ghi nhầm số tiền")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.advanceBalance").value(0))
                .andExpect(jsonPath("$.data.advances[1].reversed").value(true))
                .andExpect(jsonPath("$.data.advances[0].type").value("REVERSAL"))
                .andExpect(jsonPath("$.data.advances[0].reversalOfId").value(advanceId));

        reverseAdvance(bocId, advanceId, "Lần 2").andExpect(status().isConflict());
        long reversalId = advanceLedger(bocId).get("advances").get(0).get("id").asLong();
        reverseAdvance(bocId, reversalId, "Hoàn dòng hoàn").andExpect(status().isConflict());
        assertAdvanceBalanceMatchesLedger(bocId);
    }

    @Test
    @DisplayName("BR-EPAY-04: khoản ứng đã trừ một phần vào lương - không hoàn được (số dư không đủ); hoàn khoản trừ lương trước rồi mới hoàn được")
    void reverseAdvanceShouldRequireEnoughBalance() throws Exception {
        finalizeMonth();
        long advanceId = advance(bocId, Map.of("cashAmount", 200000)).get("advances").get(0).get("id").asLong();
        long deductionId = payOk(bocId, Map.of("advanceDeduction", 150000)).get("payments").get(0).get("id").asLong();

        reverseAdvance(bocId, advanceId, "Ghi nhầm")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("chỉ còn 50.000 đ")));
        assertThat(advanceBalance(bocId)).isEqualByComparingTo("50000");

        reversePayment(bocId, deductionId, "Trừ nhầm").andExpect(status().isOk());
        reverseAdvance(bocId, advanceId, "Ghi nhầm").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.advanceBalance").value(0));
        assertAdvanceBalanceMatchesLedger(bocId);
    }

    // ===== Công nợ nhân viên =====

    @Test
    @DisplayName("BR-EPAY-01, 03: công nợ - lương chưa trả + dư tạm ứng, bỏ người đã trả đủ / không nợ gì, lương chưa trả nhiều lên đầu")
    void debtsShouldCombineUnpaidSalaryAndAdvance() throws Exception {
        finalizeMonth();
        advance(bocId, Map.of("cashAmount", 100000));
        payOk(bocId, Map.of("cashAmount", 200000));              // Bốc: còn 400.000 lương, dư tạm ứng 100.000
        payOk(keeperId, Map.of("cashAmount", 350000));            // Thủ kho: trả đủ, không nợ
        advance(idleId, Map.of("cashAmount", 70000));           // Chưa làm: chỉ có tạm ứng

        mockMvc.perform(get("/api/employee-debts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].employeeId").value(bocId))
                .andExpect(jsonPath("$.data[0].unpaidSalaryAmount").value(400000))
                .andExpect(jsonPath("$.data[0].advanceBalance").value(100000))
                .andExpect(jsonPath("$.data[0].netAmount").value(300000))
                .andExpect(jsonPath("$.data[0].unpaidMonths.length()").value(1))
                .andExpect(jsonPath("$.data[0].unpaidMonths[0].month").value(MONTH.toString()))
                .andExpect(jsonPath("$.data[0].unpaidMonths[0].totalAmount").value(600000))
                .andExpect(jsonPath("$.data[0].unpaidMonths[0].paidAmount").value(200000))
                .andExpect(jsonPath("$.data[0].unpaidMonths[0].outstandingAmount").value(400000))
                .andExpect(jsonPath("$.data[1].employeeId").value(idleId))
                .andExpect(jsonPath("$.data[1].unpaidSalaryAmount").value(0))
                .andExpect(jsonPath("$.data[1].netAmount").value(-70000))
                .andExpect(jsonPath("$.data[1].unpaidMonths.length()").value(0));

        mockMvc.perform(get("/api/employee-debts").param("employeeId", String.valueOf(idleId)))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("Mẫu API: chi tiết dòng lương trả về đủ lịch sử; hủy chốt không ảnh hưởng tháng chưa có thanh toán")
    void itemDetailShouldListPayments() throws Exception {
        finalizeMonth();
        advance(bocId, Map.of("cashAmount", 50000));
        payOk(bocId, Map.of("cashAmount", 1000, "advanceDeduction", 2000));

        mockMvc.perform(get("/api/payrolls/{month}/items/{id}", MONTH, bocId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value(MONTH.toString()))
                .andExpect(jsonPath("$.data.employeeCode").exists())
                .andExpect(jsonPath("$.data.payments.length()").value(2))
                .andExpect(jsonPath("$.data.advanceBalance").value(48000));
        mockMvc.perform(get("/api/payrolls/{month}/items/{id}", MONTH, idleId)).andExpect(status().isNotFound());
    }

    // ===== Helpers =====

    private void assertAdvanceBalanceMatchesLedger(long employeeId) {
        entityManager.flush();
        BigDecimal fromLedger = jdbcTemplate.queryForObject("""
                SELECT COALESCE((SELECT sum(CASE type WHEN 'ADVANCE' THEN amount ELSE -amount END)
                                   FROM employee_advance WHERE employee_id = ?), 0)
                     - COALESCE((SELECT sum(CASE type WHEN 'PAYMENT' THEN amount ELSE -amount END)
                                   FROM payroll_payment WHERE employee_id = ? AND method = 'ADVANCE_DEDUCTION'), 0)
                """, BigDecimal.class, employeeId, employeeId);
        assertThat(advanceBalance(employeeId)).as("số dư tạm ứng phải khớp nhật ký").isEqualByComparingTo(fromLedger);
    }

    private BigDecimal advanceBalance(long employeeId) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT advance_balance FROM employee WHERE id = ?", BigDecimal.class, employeeId);
    }

    private int paymentRows() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM payroll_payment", Integer.class);
    }

    /**
     * Giả lập bảng lương chốt trước module — đúng việc migration V18 làm với dữ liệu cũ.
     * clear(): bảng lương đang nằm trong persistence context với tracksDebt = true, phải bỏ để lần đọc sau lấy từ DB.
     */
    private void markLegacy() {
        entityManager.flush();
        jdbcTemplate.update("UPDATE payroll SET tracks_debt = FALSE WHERE period = ?", MONTH.atDay(1));
        entityManager.clear();
    }

    private long[] paymentIds(long employeeId) throws Exception {
        String response = mockMvc.perform(get("/api/payrolls/{month}/items/{id}", MONTH, employeeId))
                .andReturn().getResponse().getContentAsString();
        JsonNode payments = objectMapper.readTree(response).get("data").get("payments");
        long[] ids = new long[payments.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = payments.get(i).get("id").asLong();
        }
        return ids;
    }

    private JsonNode advanceLedger(long employeeId) throws Exception {
        String response = mockMvc.perform(get("/api/employees/{id}/advances", employeeId))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    /** Trả lương và khẳng định 201; trả lại dữ liệu (tình trạng dòng lương + lịch sử). */
    private JsonNode payOk(long employeeId, Map<String, Object> request) throws Exception {
        String response = pay(employeeId, request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    private ResultActions pay(long employeeId, Map<String, Object> request) throws Exception {
        return payRequest(employeeId, request);
    }

    private ResultActions payRequest(long employeeId, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/api/payrolls/{month}/items/{id}/payments", MONTH, employeeId)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions reversePayment(long employeeId, long paymentId, String reason) throws Exception {
        return mockMvc.perform(post("/api/payrolls/{month}/items/{id}/payments/{paymentId}/reverse",
                MONTH, employeeId, paymentId)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private JsonNode advance(long employeeId, Map<String, Object> request) throws Exception {
        String response = advanceRequest(employeeId, request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    private ResultActions advanceRequest(long employeeId, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/api/employees/{id}/advances", employeeId)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions reverseAdvance(long employeeId, long advanceId, String reason) throws Exception {
        return mockMvc.perform(post("/api/employees/{id}/advances/{advanceId}/reverse", employeeId, advanceId)
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private org.springframework.test.web.servlet.result.JsonPathResultMatchers line(long employeeId, String field) {
        return jsonPath("$.data.items[?(@.employeeId == %d)].%s", employeeId, field);
    }

    private ResultActions sheet() throws Exception {
        return mockMvc.perform(get("/api/payrolls/{month}", MONTH));
    }

    private void finalizeMonth() throws Exception {
        mockMvc.perform(post("/api/payrolls/{month}/finalize", MONTH)).andExpect(status().isOk());
    }

    private ResultActions cancel(String json) throws Exception {
        return mockMvc.perform(post("/api/payrolls/{month}/cancel", MONTH)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void record(long employeeId, LocalDate date) throws Exception {
        mockMvc.perform(put("/api/attendance/{employeeId}/{date}", employeeId, date)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"FULL\"}")).andExpect(status().isOk());
    }

    private long createEmployee(String json) throws Exception {
        String body = mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asLong();
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }
}
