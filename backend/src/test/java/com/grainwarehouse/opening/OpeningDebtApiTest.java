package com.grainwarehouse.opening;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Công nợ đầu kỳ khách / NCC qua API (BR-ODB-01..09). Ghi đồng thời: OpeningDebtConcurrencyTest. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OpeningDebtApiTest {

    private static final String CUSTOMERS = "/api/opening-customer-debts";
    private static final String SUPPLIERS = "/api/opening-supplier-debts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Customer dealer;
    private Customer farm;
    private Supplier vietPhat;

    @BeforeEach
    void setUp() {
        dealer = saveCustomer("Đại lý Minh Phát", "0901000001");
        farm = saveCustomer("Trại gà Đồng Tâm", "0901000002");
        vietPhat = saveSupplier("Công ty Việt Phát");
    }

    // ===== Ghi nhận theo lô =====

    @Test
    @DisplayName("BR-ODB-01, 04: ghi nhận lô 2 khách - 201, mỗi dòng một chứng từ DKH Hoàn tất, ghi người lập")
    void shouldCreateOneDocumentPerLine() throws Exception {
        create(CUSTOMERS, body(LocalDate.of(2026, 9, 30), line(dealer.getId(), "15000000", "Nợ sổ tay cũ"),
                line(farm.getId(), "8200000", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value(matchesPattern("DKH\\d{6}")))
                .andExpect(jsonPath("$.data[0].party").value("CUSTOMER"))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].documentDate").value("2026-09-30"))
                .andExpect(jsonPath("$.data[0].partyName").value("Đại lý Minh Phát"))
                .andExpect(jsonPath("$.data[0].totalAmount").value(15000000))
                .andExpect(jsonPath("$.data[0].outstandingAmount").value(15000000))
                .andExpect(jsonPath("$.data[0].note").value("Nợ sổ tay cũ"))
                .andExpect(jsonPath("$.data[0].createdBy").exists())
                .andExpect(jsonPath("$.data[1].partyName").value("Trại gà Đồng Tâm"));

        // Không đụng tồn kho: không có dòng hàng, không có giao dịch kho
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stock_transaction", Integer.class)).isZero();
    }

    @Test
    @DisplayName("BR-ODB-02: số nợ 0 / âm / lẻ đồng, trùng khách trong lô, lô rỗng, ngày tương lai - 400 kèm field")
    void shouldValidateLines() throws Exception {
        create(CUSTOMERS, body(null, line(dealer.getId(), "0", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].amount"));
        create(CUSTOMERS, body(null, line(dealer.getId(), "-100", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].amount"));
        create(CUSTOMERS, body(null, line(dealer.getId(), "1000.5", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].amount"));
        create(CUSTOMERS, body(null, line(dealer.getId(), "1000", null), line(dealer.getId(), "2000", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[1].partyId"));
        create(CUSTOMERS, body(null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines"));
        create(CUSTOMERS, body(today().plusDays(1), line(dealer.getId(), "1000", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("documentDate"));
    }

    @Test
    @DisplayName("Khách không tồn tại - 404; khách ngừng hoạt động - 409; không dòng nào được ghi")
    void shouldRejectUnknownOrInactiveParty() throws Exception {
        create(CUSTOMERS, body(null, line(dealer.getId(), "1000", null), line(999999L, "1000", null)))
                .andExpect(status().isNotFound());
        farm.setActive(false);
        create(CUSTOMERS, body(null, line(dealer.getId(), "1000", null), line(farm.getId(), "1000", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString(farm.getCode())));

        assertThat(documentCount("OPENING_CUSTOMER_DEBT")).isZero();
    }

    @Test
    @DisplayName("BR-ODB-03: khách đã có nợ đầu kỳ - 409 liệt kê khách, cả lô không ghi (dòng hợp lệ cũng không)")
    void shouldRejectSecondOpeningDebtForSameCustomer() throws Exception {
        create(CUSTOMERS, body(null, line(dealer.getId(), "15000000", null))).andExpect(status().isCreated());

        create(CUSTOMERS, body(null, line(farm.getId(), "8200000", null), line(dealer.getId(), "500000", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString(dealer.getCode())));

        assertThat(documentCount("OPENING_CUSTOMER_DEBT")).isEqualTo(1);
    }

    // ===== Công nợ, thu tiền, sổ quỹ =====

    @Test
    @DisplayName("BR-ODB-05: trang công nợ khách cộng nợ đầu kỳ, kể cả khách chưa có phiếu bán nào")
    void customerDebtsShouldIncludeOpeningDebt() throws Exception {
        create(CUSTOMERS, body(LocalDate.of(2026, 9, 1), line(dealer.getId(), "15000000", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/customer-debts").param("customerId", dealer.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].outstandingAmount").value(15000000))
                .andExpect(jsonPath("$.data[0].openOrderCount").value(1))
                .andExpect(jsonPath("$.data[0].oldestDocumentDate").value("2026-09-01"));
    }

    @Test
    @DisplayName("BR-ODB-07: thu nợ cũ TM + CK - hai dòng thu, còn nợ giảm; vượt số còn nợ - 409; tiền vào sổ quỹ")
    void shouldCollectOpeningDebt() throws Exception {
        long id = createOne(CUSTOMERS, dealer.getId(), "15000000");

        pay(CUSTOMERS, id, "3000000", "2000000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paidAmount").value(5000000))
                .andExpect(jsonPath("$.data.outstandingAmount").value(10000000))
                .andExpect(jsonPath("$.data.payments.length()").value(2))
                .andExpect(jsonPath("$.data.payments[0].method").value("CASH"));
        pay(CUSTOMERS, id, "10000001", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("vượt số còn nợ")));

        // Sổ quỹ (VIEW) tự lấy khoản thu nợ cũ, mã chứng từ là mã DKH
        entityManager.flush();
        List<Map<String, Object>> entries = jdbcTemplate.queryForList(
                "SELECT account, amount, counterparty FROM cash_flow_entry WHERE source = 'SALE_PAYMENT'"
                        + " AND reference = (SELECT code FROM stock_document WHERE id = ?) ORDER BY account", id);
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).get("account")).isEqualTo("BANK");
        assertThat(((Number) entries.get(0).get("amount")).longValue()).isEqualTo(2000000);
        assertThat(entries.get(1).get("counterparty")).isEqualTo("Đại lý Minh Phát");
    }

    @Test
    @DisplayName("BR-ODB-06, 07: có khoản thu thì không đảo được; hoàn khoản thu rồi đảo được; đảo xong nhập lại được")
    void shouldReverseOnlyWhenNothingPaid() throws Exception {
        long id = createOne(CUSTOMERS, dealer.getId(), "15000000");
        JsonNode paid = data(pay(CUSTOMERS, id, "3000000", null).andExpect(status().isCreated()));
        long paymentId = paid.get("payments").get(0).get("id").asLong();

        reverse(CUSTOMERS, id, "Nhập nhầm số")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("hoàn các khoản")));

        mockMvc.perform(post(CUSTOMERS + "/{id}/payments/{paymentId}/reverse", id, paymentId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Thu nhầm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAmount").value(0))
                .andExpect(jsonPath("$.data.payments[0].reversed").value(true));
        reverse(CUSTOMERS, id, "Nhập nhầm số")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"))
                .andExpect(jsonPath("$.data.outstandingAmount").value(0));

        pay(CUSTOMERS, id, "1000", null).andExpect(status().isConflict());
        reverse(CUSTOMERS, id, "Lần nữa").andExpect(status().isConflict());
        create(CUSTOMERS, body(null, line(dealer.getId(), "12000000", null))).andExpect(status().isCreated());
        mockMvc.perform(get("/api/customer-debts").param("customerId", dealer.getId().toString()))
                .andExpect(jsonPath("$.data[0].outstandingAmount").value(12000000));
    }

    @Test
    @DisplayName("NCC: ghi nhận DNC, trả tiền là khoản chi trong sổ quỹ, công nợ NCC cộng nợ đầu kỳ")
    void supplierOpeningDebtShouldFlowLikeInboundDebt() throws Exception {
        create(SUPPLIERS, body(null, line(vietPhat.getId(), "34000000", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].code").value(matchesPattern("DNC\\d{6}")))
                .andExpect(jsonPath("$.data[0].party").value("SUPPLIER"));
        long id = data(mockMvc.perform(get(SUPPLIERS))).get("items").get(0).get("id").asLong();

        pay(SUPPLIERS, id, "4000000", null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.outstandingAmount").value(30000000));

        mockMvc.perform(get("/api/supplier-debts").param("supplierId", vietPhat.getId().toString()))
                .andExpect(jsonPath("$.data[0].outstandingAmount").value(30000000));
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT amount FROM cash_flow_entry WHERE source = 'SUPPLIER_PAYMENT'"
                + " AND reference = (SELECT code FROM stock_document WHERE id = ?)", Long.class, id)).isEqualTo(-4000000);
    }

    @Test
    @DisplayName("URL khách không đọc được khoản nợ NCC và ngược lại - 404")
    void partyTypesShouldNotMix() throws Exception {
        long customerDebt = createOne(CUSTOMERS, dealer.getId(), "1000000");

        mockMvc.perform(get(SUPPLIERS + "/{id}", customerDebt)).andExpect(status().isNotFound());
        pay(SUPPLIERS, customerDebt, "1000", null).andExpect(status().isNotFound());
        create(SUPPLIERS, body(null, line(dealer.getId(), "1000", null))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Danh sách: lọc theo khách, chỉ còn nợ, tìm theo tên khách; không lẫn phiếu bán")
    void listShouldFilter() throws Exception {
        long dealerDebt = createOne(CUSTOMERS, dealer.getId(), "15000000");
        createOne(CUSTOMERS, farm.getId(), "8200000");
        pay(CUSTOMERS, dealerDebt, "15000000", null).andExpect(status().isCreated());

        mockMvc.perform(get(CUSTOMERS).param("unpaidOnly", "true"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].partyName").value("Trại gà Đồng Tâm"))
                .andExpect(jsonPath("$.data.items[0].payments").doesNotExist());
        mockMvc.perform(get(CUSTOMERS).param("partyId", dealer.getId().toString()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].outstandingAmount").value(0));
        mockMvc.perform(get(CUSTOMERS).param("keyword", "dong tam"))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    // ===== Ràng buộc ở DB =====

    @Test
    @DisplayName("BR-ODB-03: UNIQUE ở DB - hai khoản nợ đầu kỳ còn hiệu lực cho một khách bị chặn")
    void databaseShouldRejectTwoActiveOpeningDebts() throws Exception {
        createOne(CUSTOMERS, dealer.getId(), "1000000");
        entityManager.flush();
        assertThatThrownBy(() -> insertDebt("DKH-X1", "OPENING_CUSTOMER_DEBT", dealer.getId(), null, "COMPLETED"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_stock_document_opening_customer_debt");
    }

    @Test
    @DisplayName("CHECK ở DB - chứng từ kho (phiếu nhập) vẫn bắt buộc có kho sau khi warehouse_id thành nullable")
    void databaseShouldStillRequireWarehouseForStockDocuments() {
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, supplier_id, document_date)"
                + " VALUES ('PN-X1', 'INBOUND', ?, CURRENT_DATE)", vietPhat.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_warehouse");
    }

    @Test
    @DisplayName("CHECK ở DB - nợ đầu kỳ phải > 0")
    void databaseShouldRejectZeroOpeningDebt() {
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, customer_id, document_date,"
                + " status, completed_at, total_amount) VALUES ('DKH-X2', 'OPENING_CUSTOMER_DEBT', ?, CURRENT_DATE,"
                + " 'COMPLETED', now(), 0)", dealer.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_opening_debt_amount");
    }

    // ===== Helpers =====

    private long createOne(String base, Long partyId, String amount) throws Exception {
        return data(create(base, body(null, line(partyId, amount, null))).andExpect(status().isCreated()))
                .get(0).get("id").asLong();
    }

    private ResultActions create(String base, String body) throws Exception {
        return mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions pay(String base, long id, String cash, String transfer) throws Exception {
        String body = "{" + (cash == null ? "" : "\"cashAmount\": " + cash)
                + (cash != null && transfer != null ? ", " : "")
                + (transfer == null ? "" : "\"transferAmount\": " + transfer) + "}";
        return mockMvc.perform(post(base + "/{id}/payments", id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions reverse(String base, long id, String reason) throws Exception {
        return mockMvc.perform(post(base + "/{id}/reverse", id).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private static String body(LocalDate date, String... lines) {
        String dateField = date == null ? "" : "\"documentDate\": \"" + date + "\", ";
        return "{" + dateField + "\"lines\": [" + String.join(", ", lines) + "]}";
    }

    private static String line(Long partyId, String amount, String note) {
        String noteField = note == null ? "" : ", \"note\": \"" + note + "\"";
        return "{\"partyId\": " + partyId + ", \"amount\": " + amount + noteField + "}";
    }

    private void insertDebt(String code, String type, Long customerId, Long supplierId, String status) {
        jdbcTemplate.update("INSERT INTO stock_document (code, type, customer_id, supplier_id, document_date, status,"
                + " completed_at, total_amount) VALUES (?, ?, ?, ?, CURRENT_DATE, ?, now(), 1000)",
                code, type, customerId, supplierId, status);
    }

    private int documentCount(String type) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM stock_document WHERE type = ?", Integer.class, type);
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }

    private Customer saveCustomer(String name, String phone) {
        Customer c = new Customer();
        c.setName(name);
        c.setType(CustomerType.DEALER);
        c.setPhone(phone);
        return customerRepository.save(c);
    }

    private Supplier saveSupplier(String name) {
        Supplier s = new Supplier();
        s.setName(name);
        return supplierRepository.save(s);
    }
}
