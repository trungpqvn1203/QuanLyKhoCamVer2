package com.grainwarehouse.cashbook;

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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sổ quỹ (BR-CASH-01..07) trên DB thật; mỗi test rollback. Các khoản thanh toán của 4 module có sẵn được chèn thẳng
 * bằng SQL (chỉ cần đúng hàng trong bảng để VIEW cash_flow_entry đọc), phiếu thu/chi khác đi qua API.
 * Ngày cố định trong tháng 01/2026 (luôn ở quá khứ) để số dư không phụ thuộc hôm nay.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CashBookApiTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private long incomeCategory;
    private long expenseCategory;

    @BeforeEach
    void setUp() throws Exception {
        incomeCategory = createCategory("Chủ góp vốn", "IN");
        expenseCategory = createCategory("Tiền điện", "OUT");
    }

    // ===== Chưa đặt đầu kỳ =====

    @Test
    @DisplayName("BR-CASH-03: chưa đặt đầu kỳ - tổng quan configured=false; xem sổ / lập phiếu - 409")
    void shouldRequireOpeningFirst() throws Exception {
        mockMvc.perform(get("/api/cash-book/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.cash").doesNotExist());
        mockMvc.perform(get("/api/cash-book").param("account", "CASH"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Chưa đặt đầu kỳ")));
        voucher(Map.of("direction", "OUT", "account", "CASH", "categoryId", expenseCategory, "amount", 1000))
                .andExpect(status().isConflict());
    }

    // ===== Đầu kỳ =====

    @Test
    @DisplayName("BR-CASH-03: đặt rồi sửa đầu kỳ - mỗi lần ghi lịch sử kèm giá trị cũ, người sửa (mới nhất trước)")
    void shouldRecordOpeningHistory() throws Exception {
        setOpening(START, 1_000_000, 2_000_000)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.startDate").value(START.toString()))
                .andExpect(jsonPath("$.data.changes.length()").value(1))
                .andExpect(jsonPath("$.data.changes[0].oldStartDate").doesNotExist())
                .andExpect(jsonPath("$.data.changes[0].changedBy").exists());

        setOpening(START.plusDays(1), 1_500_000, 2_000_000)
                .andExpect(jsonPath("$.data.openingCash").value(1500000))
                .andExpect(jsonPath("$.data.changes.length()").value(2))
                .andExpect(jsonPath("$.data.changes[0].oldStartDate").value(START.toString()))
                .andExpect(jsonPath("$.data.changes[0].oldOpeningCash").value(1000000))
                .andExpect(jsonPath("$.data.changes[0].newOpeningCash").value(1500000));
        mockMvc.perform(get("/api/cash-book/opening")).andExpect(jsonPath("$.data.changes.length()").value(2));
    }

    @Test
    @DisplayName("BR-CASH-03: ngày đầu kỳ ở tương lai / số âm / lẻ đồng / thiếu - 400")
    void shouldValidateOpening() throws Exception {
        setOpening(today().plusDays(1), 0, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("startDate"));
        setOpening(START, -1, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("openingCash"));
        mockMvc.perform(put("/api/cash-book/opening").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\": \"2026-01-10\", \"openingCash\": 0, \"openingBank\": 10.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("openingBank"));
        mockMvc.perform(put("/api/cash-book/opening").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingCash\": 0, \"openingBank\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("startDate"));
    }

    // ===== Sổ quỹ =====

    @Test
    @DisplayName("BR-CASH-02: sổ tiền mặt gộp 5 nguồn đúng dấu, bỏ trừ tạm ứng và khoản trước đầu kỳ, số dư lũy kế từng dòng")
    void cashLedgerShouldCombineAllSources() throws Exception {
        setOpening(START, 1_000_000, 2_000_000);
        seedEntries();

        ledger("CASH", "2026-01-01", "2026-01-31")
                .andExpect(status().isOk())
                // from sớm hơn đầu kỳ → kéo về ngày đầu kỳ
                .andExpect(jsonPath("$.data.from").value(START.toString()))
                .andExpect(jsonPath("$.data.openingBalance").value(1000000))
                .andExpect(jsonPath("$.data.entries.length()").value(6))
                .andExpect(jsonPath("$.data.entries[0].source").value("SALE_PAYMENT"))
                .andExpect(jsonPath("$.data.entries[0].amount").value(5000000))
                .andExpect(jsonPath("$.data.entries[0].direction").value("IN"))
                .andExpect(jsonPath("$.data.entries[0].reference").value("TXPX1"))
                .andExpect(jsonPath("$.data.entries[0].counterparty").value("Đại lý Sổ Quỹ"))
                .andExpect(jsonPath("$.data.entries[0].balance").value(6000000))
                .andExpect(jsonPath("$.data.entries[1].source").value("SUPPLIER_PAYMENT"))
                .andExpect(jsonPath("$.data.entries[1].amount").value(-2000000))
                .andExpect(jsonPath("$.data.entries[1].balance").value(4000000))
                .andExpect(jsonPath("$.data.entries[2].source").value("PAYROLL_PAYMENT"))
                .andExpect(jsonPath("$.data.entries[2].amount").value(-1000000))
                .andExpect(jsonPath("$.data.entries[2].reference").value("Lương 12/2019"))
                .andExpect(jsonPath("$.data.entries[3].source").value("EMPLOYEE_ADVANCE"))
                .andExpect(jsonPath("$.data.entries[3].amount").value(-500000))
                .andExpect(jsonPath("$.data.entries[4].source").value("CASH_VOUCHER"))
                .andExpect(jsonPath("$.data.entries[4].amount").value(200000))
                .andExpect(jsonPath("$.data.entries[4].category").value("Chủ góp vốn"))
                .andExpect(jsonPath("$.data.entries[5].amount").value(-100000))
                .andExpect(jsonPath("$.data.entries[5].balance").value(2600000))
                .andExpect(jsonPath("$.data.totalIn").value(5200000))
                .andExpect(jsonPath("$.data.totalOut").value(3600000))
                .andExpect(jsonPath("$.data.closingBalance").value(2600000))
                .andExpect(jsonPath("$.data.negative").value(false));
    }

    @Test
    @DisplayName("BR-CASH-01: tài khoản tách riêng tiền mặt - chỉ khoản chuyển khoản; số dư đầu khoảng giữa kỳ đúng")
    void bankLedgerAndMidRangeOpeningShouldBeCorrect() throws Exception {
        setOpening(START, 1_000_000, 2_000_000);
        seedEntries();

        ledger("BANK", "2026-01-01", "2026-01-31")
                .andExpect(jsonPath("$.data.openingBalance").value(2000000))
                .andExpect(jsonPath("$.data.entries.length()").value(2))
                .andExpect(jsonPath("$.data.entries[0].amount").value(3000000))
                .andExpect(jsonPath("$.data.entries[1].amount").value(-1000000))
                .andExpect(jsonPath("$.data.closingBalance").value(4000000));

        // 12–13/01: đầu khoảng = 1.000.000 + 5.000.000 − 2.000.000
        ledger("CASH", "2026-01-12", "2026-01-13")
                .andExpect(jsonPath("$.data.openingBalance").value(4000000))
                .andExpect(jsonPath("$.data.entries.length()").value(2))
                .andExpect(jsonPath("$.data.closingBalance").value(2500000));
    }

    @Test
    @DisplayName("BR-CASH-02: lọc theo nguồn - tổng chỉ của nguồn đó, không có số dư lũy kế; đầu/cuối khoảng vẫn của cả tài khoản")
    void sourceFilterShouldKeepAccountBalances() throws Exception {
        setOpening(START, 1_000_000, 2_000_000);
        seedEntries();

        mockMvc.perform(get("/api/cash-book").param("account", "CASH").param("from", "2026-01-01")
                        .param("to", "2026-01-31").param("source", "SUPPLIER_PAYMENT"))
                .andExpect(jsonPath("$.data.source").value("SUPPLIER_PAYMENT"))
                .andExpect(jsonPath("$.data.entries.length()").value(1))
                .andExpect(jsonPath("$.data.entries[0].balance").doesNotExist())
                .andExpect(jsonPath("$.data.totalIn").value(0))
                .andExpect(jsonPath("$.data.totalOut").value(2000000))
                .andExpect(jsonPath("$.data.closingBalance").value(2600000));
    }

    @Test
    @DisplayName("BR-CASH-06: tổng quan - số dư tới hôm nay của cả hai tài khoản; quỹ âm báo negative (không chặn khoản chi)")
    void summaryShouldShowBalancesAndFlagNegative() throws Exception {
        setOpening(START, 1_000_000, 2_000_000);
        seedEntries();
        mockMvc.perform(get("/api/cash-book/summary"))
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.cash.balance").value(2600000))
                .andExpect(jsonPath("$.data.bank.balance").value(4000000))
                .andExpect(jsonPath("$.data.cash.negative").value(false));

        // Chi vượt quỹ vẫn ghi được — chỉ cảnh báo
        voucher(Map.of("direction", "OUT", "account", "CASH", "categoryId", expenseCategory, "amount", 3000000,
                "voucherDate", "2026-01-20")).andExpect(status().isCreated());
        mockMvc.perform(get("/api/cash-book/summary"))
                .andExpect(jsonPath("$.data.cash.balance").value(-400000))
                .andExpect(jsonPath("$.data.cash.negative").value(true));
        ledger("CASH", "2026-01-01", "2026-01-31").andExpect(jsonPath("$.data.negative").value(true));
    }

    @Test
    @DisplayName("Khoảng ngày sai - đến trước từ / quá 366 ngày / đến trước đầu kỳ / thiếu account - 400")
    void shouldValidateLedgerRange() throws Exception {
        setOpening(START, 0, 0);

        ledger("CASH", "2026-01-20", "2026-01-15")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("to"));
        ledger("CASH", "2024-01-01", "2027-06-01")
                .andExpect(status().isBadRequest());
        ledger("CASH", "2025-12-01", "2026-01-05")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("to"));
        mockMvc.perform(get("/api/cash-book"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("account"));
        mockMvc.perform(get("/api/cash-book").param("account", "WALLET")).andExpect(status().isBadRequest());
    }

    // ===== Phiếu thu / chi khác =====

    @Test
    @DisplayName("BR-CASH-04: lập phiếu thu PT / phiếu chi PC; hoàn - mã -H, dấu ngược trong sổ; hoàn lần 2 / hoàn phiếu hoàn - 409")
    void shouldCreateAndReverseVouchers() throws Exception {
        setOpening(START, 0, 0);
        voucher(Map.of("direction", "IN", "account", "BANK", "categoryId", incomeCategory, "amount", 5000000,
                "voucherDate", "2026-01-11", "counterparty", "Anh Trung", "note", "Góp vốn đợt 1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(matchesPattern("PT\\d{6}")))
                .andExpect(jsonPath("$.data.type").value("VOUCHER"))
                .andExpect(jsonPath("$.data.categoryName").value("Chủ góp vốn"));
        JsonNode expense = voucherOk(Map.of("direction", "OUT", "account", "CASH", "categoryId", expenseCategory,
                "amount", 300000, "voucherDate", "2026-01-12"));
        String code = expense.get("code").asText();
        long id = expense.get("id").asLong();
        org.assertj.core.api.Assertions.assertThat(code).matches("PC\\d{6}");

        reverse(id, " ").andExpect(status().isBadRequest());
        JsonNode reversal = objectMapper.readTree(reverse(id, "Ghi nhầm số tiền")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(code + "-H"))
                .andExpect(jsonPath("$.data.type").value("REVERSAL"))
                .andExpect(jsonPath("$.data.reversalOfCode").value(code))
                .andExpect(jsonPath("$.data.voucherDate").value(today().toString()))
                .andReturn().getResponse().getContentAsString()).get("data");

        reverse(id, "Lần 2").andExpect(status().isConflict());
        reverse(reversal.get("id").asLong(), "Hoàn phiếu hoàn").andExpect(status().isConflict());

        // Trong sổ: phiếu chi −300.000 (đã hoàn), phiếu hoàn +300.000 → tiền mặt về 0
        ledger("CASH", "2026-01-01", today().toString())
                .andExpect(jsonPath("$.data.entries[0].amount").value(-300000))
                .andExpect(jsonPath("$.data.entries[0].reversed").value(true))
                .andExpect(jsonPath("$.data.entries[1].amount").value(300000))
                .andExpect(jsonPath("$.data.entries[1].reversal").value(true))
                .andExpect(jsonPath("$.data.closingBalance").value(0));
        mockMvc.perform(get("/api/cash-vouchers").param("direction", "OUT"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[1].reversed").value(true));
        mockMvc.perform(get("/api/cash-vouchers").param("keyword", "anh trung"))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("BR-CASH-04, 05: danh mục khác chiều - 400 categoryId; ngừng dùng - 409; trước đầu kỳ - 400 voucherDate; số tiền 0 - 400")
    void shouldValidateVoucher() throws Exception {
        setOpening(START, 0, 0);

        voucher(Map.of("direction", "IN", "account", "CASH", "categoryId", expenseCategory, "amount", 1000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("categoryId"));
        voucher(Map.of("direction", "OUT", "account", "CASH", "categoryId", expenseCategory, "amount", 1000,
                "voucherDate", "2026-01-09"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("voucherDate"));
        voucher(Map.of("direction", "OUT", "account", "CASH", "categoryId", expenseCategory, "amount", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("amount"));
        voucher(Map.of("direction", "OUT", "account", "CASH", "categoryId", 999_999, "amount", 1000))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/cash-categories/{id}", expenseCategory).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Tiền điện\", \"active\": false}")).andExpect(status().isOk());
        voucher(Map.of("direction", "OUT", "account", "CASH", "categoryId", expenseCategory, "amount", 1000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("ngừng dùng")));
    }

    @Test
    @DisplayName("BR-CASH-05: danh mục trùng tên (khác hoa thường) - 409; đổi tên, ngừng dùng; danh sách thu trước chi sau")
    void shouldManageCategories() throws Exception {
        mockMvc.perform(post("/api/cash-categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"tiền ĐIỆN\", \"direction\": \"OUT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
        mockMvc.perform(post("/api/cash-categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Xăng xe\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("direction"));

        mockMvc.perform(put("/api/cash-categories/{id}", expenseCategory).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Điện nước\", \"active\": true}"))
                .andExpect(jsonPath("$.data.name").value("Điện nước"))
                .andExpect(jsonPath("$.data.direction").value("OUT"));
        mockMvc.perform(get("/api/cash-categories"))
                .andExpect(jsonPath("$.data[?(@.id == %d)].direction", incomeCategory).value("IN"));
    }

    // ===== Helpers =====

    /**
     * Một khoản của mỗi nguồn (ngày khác nhau để thứ tự rõ ràng):
     * tiền mặt: 05/01 thu khách 999 (TRƯỚC đầu kỳ — không tính) · 10/01 thu khách +5tr · 11/01 trả NCC −2tr ·
     * 12/01 trả lương −1tr (+ trừ tạm ứng 300k — không phải tiền, không tính) · 13/01 tạm ứng −500k ·
     * 14/01 phiếu thu +200k · 15/01 phiếu chi −100k. Tài khoản: 10/01 thu khách +3tr · 11/01 trả NCC −1tr.
     */
    private void seedEntries() throws Exception {
        long warehouse = id("INSERT INTO warehouse (name, type, address) VALUES ('Kho sổ quỹ', 'CENTRAL', 'x') RETURNING id");
        long customer = id("INSERT INTO customer (name, type, phone) VALUES ('Đại lý Sổ Quỹ', 'DEALER', '0909111222') RETURNING id");
        long supplier = id("INSERT INTO supplier (name) VALUES ('NCC Sổ Quỹ') RETURNING id");
        long employee = id("INSERT INTO employee (name, daily_wage) VALUES ('NV Sổ Quỹ', 300000) RETURNING id");
        long sale = id("INSERT INTO stock_document (code, type, status, warehouse_id, customer_id, document_date,"
                + " total_amount, completed_at) VALUES ('TXPX1', 'OUTBOUND', 'COMPLETED', ?, ?, DATE '2026-01-05',"
                + " 100000000, now()) RETURNING id", warehouse, customer);
        long inbound = id("INSERT INTO stock_document (code, type, status, warehouse_id, supplier_id, document_date,"
                + " total_amount, completed_at) VALUES ('TXPN1', 'INBOUND', 'COMPLETED', ?, ?, DATE '2026-01-05',"
                + " 100000000, now()) RETURNING id", warehouse, supplier);
        long payroll = id("INSERT INTO payroll (period, status, total_amount, finalized_at)"
                + " VALUES (DATE '2019-12-01', 'FINALIZED', 2000000, now()) RETURNING id");
        jdbcTemplate.update("INSERT INTO payroll_item (payroll_id, employee_id, work_units, unload_tons, delivery_tons,"
                + " day_amount, unload_amount, delivery_amount, total_amount) VALUES (?, ?, 1, 0, 0, 2000000, 0, 0, 2000000)",
                payroll, employee);

        String salePayment = "INSERT INTO sale_payment (document_id, type, method, amount, payment_date) VALUES (?, 'PAYMENT', ?, ?, ?::date)";
        jdbcTemplate.update(salePayment, sale, "CASH", 999, "2026-01-05");
        jdbcTemplate.update(salePayment, sale, "CASH", 5000000, "2026-01-10");
        jdbcTemplate.update(salePayment, sale, "BANK_TRANSFER", 3000000, "2026-01-10");
        String supplierPayment = "INSERT INTO supplier_payment (document_id, type, method, amount, payment_date) VALUES (?, 'PAYMENT', ?, ?, ?::date)";
        jdbcTemplate.update(supplierPayment, inbound, "CASH", 2000000, "2026-01-11");
        jdbcTemplate.update(supplierPayment, inbound, "BANK_TRANSFER", 1000000, "2026-01-11");
        String payrollPayment = "INSERT INTO payroll_payment (payroll_id, employee_id, type, method, amount, payment_date)"
                + " VALUES (?, ?, 'PAYMENT', ?, ?, DATE '2026-01-12')";
        jdbcTemplate.update(payrollPayment, payroll, employee, "CASH", 1000000);
        jdbcTemplate.update(payrollPayment, payroll, employee, "ADVANCE_DEDUCTION", 300000);
        jdbcTemplate.update("INSERT INTO employee_advance (employee_id, type, method, amount, advance_date)"
                + " VALUES (?, 'ADVANCE', 'CASH', 500000, DATE '2026-01-13')", employee);

        voucherOk(Map.of("direction", "IN", "account", "CASH", "categoryId", incomeCategory, "amount", 200000,
                "voucherDate", "2026-01-14"));
        voucherOk(Map.of("direction", "OUT", "account", "CASH", "categoryId", expenseCategory, "amount", 100000,
                "voucherDate", "2026-01-15"));
    }

    private long id(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    private ResultActions setOpening(LocalDate startDate, long cash, long bank) throws Exception {
        ResultActions result = mockMvc.perform(put("/api/cash-book/opening").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "startDate", startDate.toString(), "openingCash", cash, "openingBank", bank))));
        entityManager.flush();
        return result;
    }

    private ResultActions ledger(String account, String from, String to) throws Exception {
        return mockMvc.perform(get("/api/cash-book").param("account", account).param("from", from).param("to", to));
    }

    private ResultActions voucher(Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/cash-vouchers").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new HashMap<>(body))));
    }

    private JsonNode voucherOk(Map<String, Object> body) throws Exception {
        String response = voucher(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    private ResultActions reverse(long id, String reason) throws Exception {
        return mockMvc.perform(post("/api/cash-vouchers/{id}/reverse", id).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private long createCategory(String name, String direction) throws Exception {
        String response = mockMvc.perform(post("/api/cash-categories").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name, "direction", direction))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }
}
