package com.grainwarehouse.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.entity.WarehouseType;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Luồng phiếu bán, thanh toán, công nợ và bảng giá qua API (BR-SAL-01..15). Test rollback sau mỗi ca —
 * các ca cần commit thật (đồng thời, rollback giữa chừng khi thiếu tồn) nằm ở SalesOrderConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesOrderApiTest {

    private static final String BASE = "/api/sales-orders";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductUnitRepository unitRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Warehouse warehouse;
    private Customer dealer;
    private Customer farm;
    private Product pigFeed;
    private Product chickenFeed;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("Cám test");
        categoryRepository.save(category);
        pigFeed = saveProduct(category, "Cám heo con 25kg");
        chickenFeed = saveProduct(category, "Cám gà thịt 25kg");
        warehouse = saveWarehouse("Kho Hà Nội");
        dealer = saveCustomer("Đại lý Minh Phát", "0901000001");
        farm = saveCustomer("Trại heo Hưng Thịnh", "0901000002");
        setStock(pigFeed, 100);
        setStock(chickenFeed, 50);
    }

    // ===== Phiếu nháp =====

    @Test
    @DisplayName("BR-SAL-02, BR-SAL-03: tạo nháp - 201, mã PX, thành tiền làm tròn, còn nợ = 0 khi chưa hoàn tất, chưa đổi tồn")
    void shouldCreateDraftWithoutTouchingStock() throws Exception {
        create(body(dealer.getId(), null, line(pigFeed.getId(), 3, "12345.50"), line(chickenFeed.getId(), 10, "250000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(matchesPattern("PX\\d{6}")))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.documentDate").value(today().toString()))
                .andExpect(jsonPath("$.data.customerCode").value(dealer.getCode()))
                .andExpect(jsonPath("$.data.customerName").value("Đại lý Minh Phát"))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.data.lines[0].lineAmount").value(37037))   // 37.036,50 → 37.037
                .andExpect(jsonPath("$.data.lines[0].orderedQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.totalAmount").value(2537037))
                .andExpect(jsonPath("$.data.paidAmount").value(0))
                .andExpect(jsonPath("$.data.outstandingAmount").value(0))
                // BR-PRT-03: 3 bao × 25 kg = 0,075 tấn; 10 bao × 25 kg = 0,25 tấn → tổng 0,325
                .andExpect(jsonPath("$.data.lines[0].tons").value(0.075))
                .andExpect(jsonPath("$.data.lines[1].tons").value(0.25))
                .andExpect(jsonPath("$.data.totalTons").value(0.325))
                .andExpect(jsonPath("$.data.payments.length()").value(0));

        assertThat(stock(pigFeed)).isEqualTo(100);
        assertThat(transactionRows()).isZero();
    }

    @Test
    @DisplayName("BR-SAL-01: thiếu khách hàng - 400 field customerId")
    void shouldRequireCustomer() throws Exception {
        create(body(null, null, line(pigFeed.getId(), 3, "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("customerId"));
    }

    @Test
    @DisplayName("BR-SAL-01: số lượng 0 - 400; SP lặp dòng - 400; ngày tương lai - 400")
    void shouldValidateLines() throws Exception {
        create(body(dealer.getId(), null, line(pigFeed.getId(), 0, "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].quantity"));
        create(body(dealer.getId(), null, line(pigFeed.getId(), 1, "1000"), line(pigFeed.getId(), 2, "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[1].productId"));
        create(body(dealer.getId(), today().plusDays(1), line(pigFeed.getId(), 1, "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("documentDate"));
    }

    @Test
    @DisplayName("BR-SAL-15: khách ngừng hoạt động - 409; khách không tồn tại - 404")
    void shouldRejectInactiveOrUnknownCustomer() throws Exception {
        dealer.setActive(false);
        customerRepository.saveAndFlush(dealer);

        create(body(dealer.getId(), null, line(pigFeed.getId(), 1, "1000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.message").value(containsString("ngừng hoạt động")));
        create(body(999999L, null, line(pigFeed.getId(), 1, "1000")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-SAL-02: nháp KHÔNG giữ chỗ tồn - lập nháp vượt tồn vẫn được, chỉ chặn khi hoàn tất")
    void draftMayExceedStock() throws Exception {
        create(body(dealer.getId(), null, line(pigFeed.getId(), 500, "1000")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("BR-SAL-04: sửa nháp thay toàn bộ dòng; phiếu đã hoàn tất - 409")
    void shouldUpdateOnlyDraft() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 3, "1000")).get("id").asLong();

        update(id, body(farm.getId(), null, line(chickenFeed.getId(), 2, "5000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerName").value("Trại heo Hưng Thịnh"))
                .andExpect(jsonPath("$.data.lines.length()").value(1))
                .andExpect(jsonPath("$.data.lines[0].productCode").value(chickenFeed.getCode()))
                .andExpect(jsonPath("$.data.totalAmount").value(10000));

        complete(id).andExpect(status().isOk());
        update(id, body(farm.getId(), null, line(chickenFeed.getId(), 1, "5000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Hoàn tất")));
    }

    // ===== Hoàn tất / hủy / đảo =====

    @Test
    @DisplayName("BR-SAL-02: hoàn tất - trừ tồn mọi dòng, mỗi dòng một OUT có before/after; hoàn tất lần 2 - 409")
    void completeShouldDecreaseStock() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 30, "1000"), line(chickenFeed.getId(), 50, "2000"))
                .get("id").asLong();

        complete(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").exists())
                .andExpect(jsonPath("$.data.outstandingAmount").value(130000));

        assertThat(stock(pigFeed)).isEqualTo(70);
        assertThat(stock(chickenFeed)).isZero(); // bán hết đúng bằng tồn: được phép
        List<Map<String, Object>> transactions = transactionsOf(id);
        assertThat(transactions).hasSize(2).allSatisfy(t -> assertThat(t.get("type")).isEqualTo("OUT"));
        assertThat(transactions.get(0).get("quantity_before")).isEqualTo(100);
        assertThat(transactions.get(0).get("quantity_after")).isEqualTo(70);

        complete(id).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("BR-INV-01: hoàn tất vượt tồn - 409 nêu SP và tồn hiện tại")
    void completeShouldRejectInsufficientStock() throws Exception {
        long id = createDraft(line(chickenFeed.getId(), 51, "1000")).get("id").asLong();

        complete(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString(chickenFeed.getCode())))
                .andExpect(jsonPath("$.error.message").value(containsString("còn 50")));
    }

    @Test
    @DisplayName("BR-SAL-15: SP ngừng kinh doanh sau khi lưu nháp - hoàn tất bị chặn")
    void completeShouldRecheckProducts() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 1, "1000")).get("id").asLong();
        pigFeed.setActive(false);
        productRepository.saveAndFlush(pigFeed);

        complete(id).andExpect(status().isConflict());
        assertThat(stock(pigFeed)).isEqualTo(100);
    }

    @Test
    @DisplayName("Hủy nháp - CANCELLED, không đổi tồn; hủy phiếu đã hoàn tất - 409")
    void shouldCancelOnlyDraft() throws Exception {
        long draft = createDraft(line(pigFeed.getId(), 1, "1000")).get("id").asLong();
        mockMvc.perform(post(BASE + "/{id}/cancel", draft))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        long done = createDraft(line(pigFeed.getId(), 1, "1000")).get("id").asLong();
        complete(done);
        mockMvc.perform(post(BASE + "/{id}/cancel", done)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("BR-SAL-05: đảo phiếu - hàng về lại kho, mỗi OUT có một IN trỏ về nó; đảo lần 2 - 409")
    void reverseShouldRestoreStock() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 30, "1000"), line(chickenFeed.getId(), 5, "1000"))
                .get("id").asLong();
        complete(id);

        reverse(id, "Giao sai loại cám")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"))
                .andExpect(jsonPath("$.data.reason").value("Giao sai loại cám"))
                .andExpect(jsonPath("$.data.outstandingAmount").value(0)); // hàng đã về kho → không còn nợ

        assertThat(stock(pigFeed)).isEqualTo(100);
        assertThat(stock(chickenFeed)).isEqualTo(50);
        List<Map<String, Object>> transactions = transactionsOf(id);
        assertThat(transactions).hasSize(4);
        assertThat(transactions.subList(2, 4)).allSatisfy(t -> {
            assertThat(t.get("type")).isEqualTo("IN");
            assertThat(t.get("reversal_of_id")).isNotNull();
        });

        reverse(id, "Lần nữa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("chỉ được đảo một lần")));
    }

    @Test
    @DisplayName("BR-SAL-05: đảo không có lý do - 400")
    void reverseShouldRequireReason() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 1, "1000")).get("id").asLong();
        complete(id);

        reverse(id, " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("reason"));
    }

    @Test
    @DisplayName("BR-SAL-14: phiếu đã thu tiền - không đảo được; hoàn hết khoản thu rồi mới đảo được")
    void reverseShouldRequireNoPayment() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, "100000")).get("id").asLong();
        complete(id);
        long paymentId = pay(id, Map.of("cashAmount", 400000)).get("payments").get(0).get("id").asLong();

        reverse(id, "Hàng không đạt")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("400.000 đ")));
        assertThat(stock(pigFeed)).isEqualTo(90);

        reversePayment(id, paymentId, "Trả lại tiền cho khách").andExpect(status().isOk());
        reverse(id, "Hàng không đạt").andExpect(status().isOk());
        assertThat(stock(pigFeed)).isEqualTo(100);
    }

    // ===== Thanh toán =====

    @Test
    @DisplayName("BR-SAL-12: thu kết hợp tiền mặt + chuyển khoản - 201, hai dòng, đã thu/còn nợ cập nhật")
    void shouldRecordCombinedPayment() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, "100000")).get("id").asLong();
        complete(id);

        payRequest(id, Map.of("cashAmount", 300000, "transferAmount", 500000, "note", "Thu đợt 1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paidAmount").value(800000))
                .andExpect(jsonPath("$.data.outstandingAmount").value(200000))
                .andExpect(jsonPath("$.data.payments.length()").value(2))
                .andExpect(jsonPath("$.data.payments[0].method").value("CASH"))
                .andExpect(jsonPath("$.data.payments[0].amount").value(300000))
                .andExpect(jsonPath("$.data.payments[0].type").value("PAYMENT"))
                .andExpect(jsonPath("$.data.payments[0].paymentDate").value(today().toString()))
                .andExpect(jsonPath("$.data.payments[1].method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.data.payments[1].note").value("Thu đợt 1"));

        // Thu nốt đúng bằng số còn nợ: được
        payRequest(id, Map.of("transferAmount", 200000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.outstandingAmount").value(0));
    }

    @Test
    @DisplayName("BR-SAL-09: phiếu nháp chưa thu tiền được - 409")
    void shouldRejectPaymentOnDraft() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 1, "1000")).get("id").asLong();

        payRequest(id, Map.of("cashAmount", 1000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Nháp")));
    }

    @Test
    @DisplayName("BR-SAL-11: thu vượt số còn nợ - 409, không ghi dòng nào")
    void shouldRejectOverpayment() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, "100000")).get("id").asLong();
        complete(id);

        payRequest(id, Map.of("cashAmount", 600000, "transferAmount", 400001))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("1.000.001 đ")))
                .andExpect(jsonPath("$.error.message").value(containsString("1.000.000 đ")));
        assertThat(paymentRows()).isZero();
    }

    @Test
    @DisplayName("BR-SAL-12: số tiền 0/âm/lẻ - 400; ngày thu chọn tự do (tương lai, trước ngày phiếu đều được)")
    void shouldValidatePayment() throws Exception {
        long id = createDraftFrom(body(dealer.getId(), today().minusDays(3), line(pigFeed.getId(), 10, "100000")))
                .get("id").asLong();
        complete(id);

        payRequest(id, Map.of("cashAmount", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("cashAmount"));
        payRequest(id, Map.of("transferAmount", -5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("transferAmount"));
        payRequest(id, Map.of("cashAmount", new BigDecimal("1000.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("cashAmount"));
        payRequest(id, Map.of("cashAmount", 1000, "paymentDate", today().plusDays(1).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payments[0].paymentDate").value(today().plusDays(1).toString()));
        payRequest(id, Map.of("cashAmount", 1000, "paymentDate", today().minusDays(4).toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payments[1].paymentDate").value(today().minusDays(4).toString()));
    }

    @Test
    @DisplayName("BR-SAL-13: hoàn khoản thu - dòng REVERSAL trỏ về gốc, đã thu giảm; hoàn lần 2 / hoàn dòng hoàn - 409")
    void shouldReversePaymentOnce() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, "100000")).get("id").asLong();
        complete(id);
        JsonNode paid = pay(id, Map.of("cashAmount", 300000, "transferAmount", 200000));
        long cashId = paid.get("payments").get(0).get("id").asLong();

        reversePayment(id, cashId, "Ghi nhầm số tiền")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAmount").value(200000))
                .andExpect(jsonPath("$.data.outstandingAmount").value(800000))
                .andExpect(jsonPath("$.data.payments.length()").value(3))
                .andExpect(jsonPath("$.data.payments[0].reversed").value(true))
                .andExpect(jsonPath("$.data.payments[1].reversed").value(false))
                .andExpect(jsonPath("$.data.payments[2].type").value("REVERSAL"))
                .andExpect(jsonPath("$.data.payments[2].method").value("CASH"))
                .andExpect(jsonPath("$.data.payments[2].amount").value(300000))
                .andExpect(jsonPath("$.data.payments[2].reversalOfId").value(cashId))
                .andExpect(jsonPath("$.data.payments[2].note").value("Ghi nhầm số tiền"));

        reversePayment(id, cashId, "Lần nữa")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("một lần")));
        long reversalId = Long.parseLong(jdbcTemplate.queryForObject(
                "SELECT id FROM sale_payment WHERE reversal_of_id = ?", String.class, cashId));
        reversePayment(id, reversalId, "Hoàn cái hoàn").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Khoản thu của phiếu khác - 404")
    void shouldNotReversePaymentOfAnotherOrder() throws Exception {
        long first = createDraft(line(pigFeed.getId(), 1, "100000")).get("id").asLong();
        long second = createDraft(line(pigFeed.getId(), 1, "100000")).get("id").asLong();
        complete(first);
        complete(second);
        long paymentOfFirst = pay(first, Map.of("cashAmount", 1000)).get("payments").get(0).get("id").asLong();

        reversePayment(second, paymentOfFirst, "Nhầm").andExpect(status().isNotFound());
    }

    // ===== Danh sách, công nợ =====

    @Test
    @DisplayName("BR-SAL-10: lọc unpaidOnly - chỉ phiếu Hoàn tất còn nợ; lọc theo khách")
    void listShouldFilterUnpaid() throws Exception {
        long paidOff = createDraft(line(pigFeed.getId(), 1, "1000")).get("id").asLong();
        complete(paidOff);
        pay(paidOff, Map.of("cashAmount", 1000));
        long owing = createDraft(line(pigFeed.getId(), 2, "1000")).get("id").asLong();
        complete(owing);
        createDraft(line(pigFeed.getId(), 3, "1000")); // nháp: không phải nợ
        createDraftFrom(body(farm.getId(), null, line(pigFeed.getId(), 1, "1000")));

        mockMvc.perform(get(BASE).param("unpaidOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(owing))
                .andExpect(jsonPath("$.data.items[0].outstandingAmount").value(2000));
        mockMvc.perform(get(BASE).param("customerId", farm.getId().toString()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].customerName").value("Trại heo Hưng Thịnh"));
    }

    @Test
    @DisplayName("BR-SAL-10: công nợ theo khách - cộng các phiếu Hoàn tất còn nợ, bỏ nháp/đã đảo/đã trả đủ, nợ nhiều lên đầu")
    void debtsShouldSumOutstandingPerCustomer() throws Exception {
        long a = createDraftFrom(body(dealer.getId(), today().minusDays(10), line(pigFeed.getId(), 10, "1000")))
                .get("id").asLong();
        complete(a);
        pay(a, Map.of("cashAmount", 4000));                                   // còn 6.000
        long b = createDraft(line(pigFeed.getId(), 5, "1000")).get("id").asLong();
        complete(b);                                                          // còn 5.000
        long reversed = createDraft(line(pigFeed.getId(), 7, "1000")).get("id").asLong();
        complete(reversed);
        reverse(reversed, "Giao sai");
        createDraft(line(pigFeed.getId(), 9, "1000"));                        // nháp
        long farmOrder = createDraftFrom(body(farm.getId(), null, line(chickenFeed.getId(), 20, "1000")))
                .get("id").asLong();
        complete(farmOrder);                                                  // trại còn 20.000

        mockMvc.perform(get("/api/customer-debts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].customerId").value(farm.getId()))
                .andExpect(jsonPath("$.data[0].outstandingAmount").value(20000))
                .andExpect(jsonPath("$.data[1].customerId").value(dealer.getId()))
                .andExpect(jsonPath("$.data[1].outstandingAmount").value(11000))
                .andExpect(jsonPath("$.data[1].openOrderCount").value(2))
                .andExpect(jsonPath("$.data[1].oldestDocumentDate").value(today().minusDays(10).toString()));
        mockMvc.perform(get("/api/customer-debts").param("customerId", dealer.getId().toString()))
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    @DisplayName("Phiếu bán không lộ qua API phiếu nhập và ngược lại")
    void salesOrderShouldNotBeVisibleAsInboundReceipt() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 1, "1000")).get("id").asLong();

        mockMvc.perform(get("/api/inbound-receipts/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/inbound-receipts/{id}/complete", id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-SAL-04: không có DELETE phiếu bán - 405")
    void shouldNotDeleteSalesOrder() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 1, "1000")).get("id").asLong();

        mockMvc.perform(delete(BASE + "/{id}", id)).andExpect(status().isMethodNotAllowed());
    }

    // ===== Bảng giá riêng theo khách =====

    @Test
    @DisplayName("BR-SAL-06: đặt giá, ghi đè giá, xem bảng giá, bỏ giá - 204, bỏ lần 2 - 404")
    void shouldManageCustomerPrices() throws Exception {
        String prices = "/api/customers/{customerId}/prices";

        setPrice(dealer, pigFeed, "310000").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].unitPrice").value(310000));
        setPrice(dealer, pigFeed, "305000.50").andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].unitPrice").value(305000.50));
        setPrice(dealer, chickenFeed, "280000").andExpect(status().isOk());
        setPrice(farm, pigFeed, "300000").andExpect(status().isOk());

        mockMvc.perform(get(prices, dealer.getId()))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].productCode").value(pigFeed.getCode()))
                .andExpect(jsonPath("$.data[0].productName").value("Cám heo con 25kg"));
        mockMvc.perform(get(prices, farm.getId()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].unitPrice").value(300000));

        mockMvc.perform(delete(prices + "/{productId}", dealer.getId(), pigFeed.getId()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(prices + "/{productId}", dealer.getId(), pigFeed.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-SAL-06: giá âm / 3 chữ số lẻ - 400; khách hoặc SP không tồn tại - 404")
    void shouldValidateCustomerPrice() throws Exception {
        setPrice(dealer, pigFeed, "-1").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("unitPrice"));
        setPrice(dealer, pigFeed, "1000.005").andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/customers/{c}/prices/{p}", 999999L, pigFeed.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unitPrice\": 1000}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/customers/{c}/prices/{p}", dealer.getId(), 999999L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unitPrice\": 1000}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-SAL-07, 08: giá trên phiếu là giá nhập tay, đổi bảng giá sau đó không đổi phiếu đã lập")
    void orderKeepsItsOwnPrice() throws Exception {
        setPrice(dealer, pigFeed, "310000");
        long id = createDraft(line(pigFeed.getId(), 2, "300000")).get("id").asLong(); // bán thấp hơn bảng giá: được
        setPrice(dealer, pigFeed, "350000");

        mockMvc.perform(get(BASE + "/{id}", id))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(300000))
                .andExpect(jsonPath("$.data.totalAmount").value(600000));
    }

    // ===== Helpers =====

    private JsonNode createDraft(String... lines) throws Exception {
        return createDraftFrom(body(dealer.getId(), null, lines));
    }

    private JsonNode createDraftFrom(String body) throws Exception {
        String response = create(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    private JsonNode pay(long id, Map<String, Object> request) throws Exception {
        String response = payRequest(id, request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    private ResultActions create(String body) throws Exception {
        return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(long id, String body) throws Exception {
        return mockMvc.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions complete(long id) throws Exception {
        return mockMvc.perform(post(BASE + "/{id}/complete", id));
    }

    private ResultActions reverse(long id, String reason) throws Exception {
        return mockMvc.perform(post(BASE + "/{id}/reverse", id).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private ResultActions payRequest(long id, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post(BASE + "/{id}/payments", id).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private ResultActions reversePayment(long id, long paymentId, String reason) throws Exception {
        return mockMvc.perform(post(BASE + "/{id}/payments/{paymentId}/reverse", id, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private ResultActions setPrice(Customer customer, Product product, String unitPrice) throws Exception {
        return mockMvc.perform(put("/api/customers/{c}/prices/{p}", customer.getId(), product.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"unitPrice\": " + unitPrice + "}"));
    }

    // Mọi phiếu trong test xuất từ cùng một kho
    private String body(Long customerId, LocalDate date, String... lines) {
        String dateField = date == null ? "" : "\"documentDate\": \"" + date + "\", ";
        return "{\"customerId\": " + customerId + ", \"warehouseId\": " + warehouse.getId() + ", " + dateField
                + "\"lines\": [" + String.join(", ", lines) + "]}";
    }

    private static String line(Long productId, int quantity, String unitPrice) {
        return "{\"productId\": " + productId + ", \"quantity\": " + quantity + ", \"unitPrice\": " + unitPrice + "}";
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }

    private void setStock(Product product, int quantity) {
        jdbcTemplate.update("INSERT INTO inventory (product_id, warehouse_id, quantity) VALUES (?, ?, ?)",
                product.getId(), warehouse.getId(), quantity);
    }

    // JdbcTemplate đọc thẳng DB → flush trước để thấy thay đổi Hibernate còn giữ trong bộ nhớ
    private Integer stock(Product product) {
        entityManager.flush();
        return jdbcTemplate.query("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, product.getId(), warehouse.getId());
    }

    private int transactionRows() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM stock_transaction", Integer.class);
    }

    private int paymentRows() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM sale_payment", Integer.class);
    }

    private List<Map<String, Object>> transactionsOf(long documentId) {
        entityManager.flush();
        return jdbcTemplate.queryForList("SELECT t.* FROM stock_transaction t"
                + " JOIN stock_document_item i ON i.id = t.document_item_id WHERE i.document_id = ? ORDER BY t.id",
                documentId);
    }

    private Product saveProduct(Category category, String name) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setUnit(unitRepository.findByNameIgnoreCase("Bao").orElseThrow());
        product.setNetWeightKg(new BigDecimal("25"));
        return productRepository.save(product);
    }

    private Warehouse saveWarehouse(String name) {
        Warehouse w = new Warehouse();
        w.setName(name);
        w.setType(WarehouseType.CENTRAL);
        w.setAddress("Địa chỉ test");
        return warehouseRepository.save(w);
    }

    private Customer saveCustomer(String name, String phone) {
        Customer c = new Customer();
        c.setName(name);
        c.setType(CustomerType.DEALER);
        c.setPhone(phone);
        return customerRepository.save(c);
    }
}
