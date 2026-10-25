package com.grainwarehouse.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.supplier.entity.ProductSupplier;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.ProductSupplierRepository;
import com.grainwarehouse.supplier.repository.SupplierRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Trả tiền NCC trên phiếu nhập và công nợ NCC (BR-SPAY-01..05) trên DB thật; mỗi test rollback. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SupplierPaymentApiTest {

    private static final String BASE = "/api/inbound-receipts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductSupplierRepository productSupplierRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Product pigFeed;
    private Warehouse warehouse;
    private Supplier newHope;
    private Supplier deHeus;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("Cám test");
        categoryRepository.save(category);
        pigFeed = new Product();
        pigFeed.setName("Cám heo con 25kg");
        pigFeed.setCategory(category);
        pigFeed.setUnit(ProductUnit.BAG);
        pigFeed.setNetWeightKg(new BigDecimal("25"));
        productRepository.save(pigFeed);

        warehouse = new Warehouse();
        warehouse.setName("Kho Hà Nội");
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Địa chỉ test");
        warehouseRepository.save(warehouse);

        newHope = saveSupplier("Công ty New Hope");
        deHeus = saveSupplier("Công ty De Heus");
    }

    // ===== Trả tiền =====

    @Test
    @DisplayName("BR-SPAY-02: trả kết hợp tiền mặt + chuyển khoản - 201, hai dòng, đã trả/còn nợ cập nhật; trả nốt đúng số nợ")
    void shouldRecordCombinedPayment() throws Exception {
        long id = completedReceipt(newHope, 10, "100000");

        payRequest(id, Map.of("cashAmount", 300000, "transferAmount", 500000, "note", "Trả đợt 1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.tracksDebt").value(true))
                .andExpect(jsonPath("$.data.paidAmount").value(800000))
                .andExpect(jsonPath("$.data.outstandingAmount").value(200000))
                .andExpect(jsonPath("$.data.payments.length()").value(2))
                .andExpect(jsonPath("$.data.payments[0].method").value("CASH"))
                .andExpect(jsonPath("$.data.payments[0].type").value("PAYMENT"))
                .andExpect(jsonPath("$.data.payments[0].paymentDate").value(today().toString()))
                .andExpect(jsonPath("$.data.payments[1].method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.data.payments[1].note").value("Trả đợt 1"));

        payRequest(id, Map.of("transferAmount", 200000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.outstandingAmount").value(0));
        // Danh sách phiếu cũng có đã trả / còn nợ
        mockMvc.perform(get(BASE).param("supplierId", newHope.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].paidAmount").value(1000000))
                .andExpect(jsonPath("$.data.items[0].outstandingAmount").value(0));
    }

    @Test
    @DisplayName("BR-SPAY-01: phiếu nháp chưa trả tiền được - 409; còn nợ của nháp = 0")
    void shouldRejectPaymentOnDraft() throws Exception {
        long id = draft(newHope, null, 1, "1000").get("id").asLong();

        payRequest(id, Map.of("cashAmount", 1000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Chỉ phiếu Hoàn tất mới được trả tiền")));
        mockMvc.perform(get(BASE + "/{id}", id)).andExpect(jsonPath("$.data.outstandingAmount").value(0));
    }

    @Test
    @DisplayName("BR-SPAY-02: trả vượt số còn nợ - 409, không ghi dòng nào")
    void shouldRejectOverpayment() throws Exception {
        long id = completedReceipt(newHope, 10, "1000");

        payRequest(id, Map.of("cashAmount", 6000, "transferAmount", 5000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("vượt số còn nợ 10.000 đ")));
        assertThat(paymentRows()).isZero();
    }

    @Test
    @DisplayName("BR-SPAY-02: số tiền 0 / âm / lẻ đồng - 400; ngày trả chọn tự do")
    void shouldValidateAmountsAndAcceptAnyDate() throws Exception {
        long id = completedReceipt(newHope, 10, "1000");

        payRequest(id, Map.of("cashAmount", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("cashAmount"));
        payRequest(id, Map.of("cashAmount", -1)).andExpect(status().isBadRequest());
        payRequest(id, Map.of("transferAmount", 100.5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("transferAmount"));

        LocalDate future = today().plusDays(5);
        payRequest(id, Map.of("cashAmount", 1000, "paymentDate", future.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.payments[0].paymentDate").value(future.toString()));
    }

    @Test
    @DisplayName("BR-SPAY-03: hoàn khoản trả - REVERSAL trỏ về gốc, đã trả giảm; hoàn lần 2 / hoàn dòng hoàn - 409; thiếu lý do - 400")
    void shouldReversePaymentOnce() throws Exception {
        long id = completedReceipt(newHope, 10, "1000");
        long paymentId = pay(id, Map.of("cashAmount", 4000)).get("payments").get(0).get("id").asLong();

        reversePayment(id, paymentId, " ").andExpect(status().isBadRequest());
        reversePayment(id, paymentId, "Ghi nhầm số tiền")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAmount").value(0))
                .andExpect(jsonPath("$.data.outstandingAmount").value(10000))
                .andExpect(jsonPath("$.data.payments[0].reversed").value(true))
                .andExpect(jsonPath("$.data.payments[1].type").value("REVERSAL"))
                .andExpect(jsonPath("$.data.payments[1].reversalOfId").value(paymentId))
                .andExpect(jsonPath("$.data.payments[1].amount").value(4000))
                .andExpect(jsonPath("$.data.payments[1].note").value("Ghi nhầm số tiền"));

        reversePayment(id, paymentId, "Lần 2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("đã được hoàn")));
        long reversalId = paymentIds(id)[1];
        reversePayment(id, reversalId, "Hoàn dòng hoàn").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Khoản trả của phiếu khác - 404")
    void shouldNotTouchPaymentOfAnotherReceipt() throws Exception {
        long first = completedReceipt(newHope, 10, "1000");
        long second = completedReceipt(newHope, 5, "1000");
        long paymentId = pay(first, Map.of("cashAmount", 1000)).get("payments").get(0).get("id").asLong();

        reversePayment(second, paymentId, "Nhầm phiếu").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-SPAY-04: phiếu đã trả tiền - không đảo được; hoàn hết khoản trả rồi mới đảo được")
    void reverseShouldRequireNoPayment() throws Exception {
        long id = completedReceipt(newHope, 10, "100000");
        long paymentId = pay(id, Map.of("transferAmount", 400000)).get("payments").get(0).get("id").asLong();

        reverse(id, "Hàng không đạt")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("đã trả 400.000 đ")));

        reversePayment(id, paymentId, "NCC trả lại tiền").andExpect(status().isOk());
        reverse(id, "Hàng không đạt")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"))
                .andExpect(jsonPath("$.data.outstandingAmount").value(0));
    }

    @Test
    @DisplayName("BR-SPAY-05: phiếu cũ (tracks_debt = false) - còn nợ 0, không trả tiền được, không vào công nợ, vẫn đảo được")
    void legacyReceiptShouldBeTreatedAsPaid() throws Exception {
        long id = completedReceipt(newHope, 10, "1000");
        markLegacy(id);

        mockMvc.perform(get(BASE + "/{id}", id))
                .andExpect(jsonPath("$.data.tracksDebt").value(false))
                .andExpect(jsonPath("$.data.outstandingAmount").value(0));
        payRequest(id, Map.of("cashAmount", 1000))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("được coi là đã trả đủ")));
        mockMvc.perform(get("/api/supplier-debts")).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get(BASE).param("unpaidOnly", "true")).andExpect(jsonPath("$.data.items.length()").value(0));

        reverse(id, "Nhập nhầm").andExpect(status().isOk());
    }

    // ===== Công nợ NCC =====

    @Test
    @DisplayName("BR-SPAY-01: công nợ theo NCC - cộng phiếu Hoàn tất còn nợ, bỏ nháp/đã đảo/đã trả đủ/phiếu cũ, nợ nhiều lên đầu")
    void debtsShouldSumOutstandingPerSupplier() throws Exception {
        long a = draft(newHope, today().minusDays(10), 10, "1000").get("id").asLong();
        complete(a);
        pay(a, Map.of("cashAmount", 4000));                         // New Hope còn 6.000
        completedReceipt(newHope, 5, "1000");                       // New Hope còn 5.000
        long paidOff = completedReceipt(newHope, 3, "1000");
        pay(paidOff, Map.of("cashAmount", 3000));                   // trả đủ — không tính
        long reversed = completedReceipt(newHope, 7, "1000");
        reverse(reversed, "Giao sai");                              // đã đảo — không tính
        draft(newHope, null, 9, "1000");                            // nháp — không tính
        long legacy = completedReceipt(newHope, 50, "1000");
        markLegacy(legacy);                                         // phiếu cũ — không tính
        completedReceipt(deHeus, 20, "1000");                       // De Heus còn 20.000

        mockMvc.perform(get("/api/supplier-debts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].supplierId").value(deHeus.getId()))
                .andExpect(jsonPath("$.data[0].outstandingAmount").value(20000))
                .andExpect(jsonPath("$.data[1].supplierId").value(newHope.getId()))
                .andExpect(jsonPath("$.data[1].outstandingAmount").value(11000))
                .andExpect(jsonPath("$.data[1].openReceiptCount").value(2))
                .andExpect(jsonPath("$.data[1].oldestDocumentDate").value(today().minusDays(10).toString()))
                .andExpect(jsonPath("$.data[1].supplierTaxCode").value("0101044677"));
        mockMvc.perform(get("/api/supplier-debts").param("supplierId", newHope.getId().toString()))
                .andExpect(jsonPath("$.data.length()").value(1));

        // Lọc phiếu còn nợ của một NCC — màn công nợ dùng để xem chi tiết
        mockMvc.perform(get(BASE).param("supplierId", newHope.getId().toString()).param("unpaidOnly", "true"))
                .andExpect(jsonPath("$.data.items.length()").value(2));
    }

    @Test
    @DisplayName("BR-SPAY-03: DB chặn sửa/xóa dòng thanh toán NCC (trigger)")
    void dbShouldRejectChangingPayment() throws Exception {
        long id = completedReceipt(newHope, 10, "1000");
        long paymentId = pay(id, Map.of("cashAmount", 1000)).get("payments").get(0).get("id").asLong();
        entityManager.flush();

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE supplier_payment SET amount = 1 WHERE id = ?", paymentId))
                .hasMessageContaining("supplier_payment là bất biến");
    }

    // ===== Helpers =====

    private long completedReceipt(Supplier supplier, int quantity, String unitPrice) throws Exception {
        long id = draft(supplier, null, quantity, unitPrice).get("id").asLong();
        complete(id).andExpect(status().isOk());
        return id;
    }

    private JsonNode draft(Supplier supplier, LocalDate date, int quantity, String unitPrice) throws Exception {
        String dateField = date == null ? "" : "\"documentDate\": \"" + date + "\", ";
        String body = "{\"supplierId\": " + supplier.getId() + ", \"warehouseId\": " + warehouse.getId() + ", "
                + dateField + "\"lines\": [{\"productId\": " + pigFeed.getId() + ", \"orderedQuantity\": " + quantity
                + ", \"unitPrice\": " + unitPrice + "}]}";
        String response = mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    private ResultActions complete(long id) throws Exception {
        return mockMvc.perform(post(BASE + "/{id}/complete", id));
    }

    private ResultActions reverse(long id, String reason) throws Exception {
        return mockMvc.perform(post(BASE + "/{id}/reverse", id).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private JsonNode pay(long id, Map<String, Object> request) throws Exception {
        String response = payRequest(id, request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
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

    private long[] paymentIds(long id) throws Exception {
        String response = mockMvc.perform(get(BASE + "/{id}", id)).andReturn().getResponse().getContentAsString();
        JsonNode payments = objectMapper.readTree(response).get("data").get("payments");
        long[] ids = new long[payments.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = payments.get(i).get("id").asLong();
        }
        return ids;
    }

    /**
     * Giả lập phiếu hoàn tất trước khi có module này — đúng việc migration V16 làm với dữ liệu cũ.
     * clear(): phiếu đang nằm trong persistence context với tracksDebt = true, phải bỏ để lần đọc sau lấy từ DB.
     */
    private void markLegacy(long id) {
        entityManager.flush();
        jdbcTemplate.update("UPDATE stock_document SET tracks_debt = FALSE WHERE id = ?", id);
        entityManager.clear();
    }

    private int paymentRows() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM supplier_payment", Integer.class);
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }

    private Supplier saveSupplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        if (name.contains("New Hope")) {
            supplier.setTaxCode("0101044677");
        }
        supplierRepository.save(supplier);
        productSupplierRepository.save(new ProductSupplier(supplier, pigFeed));
        return supplier;
    }
}
