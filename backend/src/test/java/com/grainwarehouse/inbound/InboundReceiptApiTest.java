package com.grainwarehouse.inbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import com.grainwarehouse.supplier.entity.ProductSupplier;
import com.grainwarehouse.supplier.entity.ProductSupplierId;
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
 * Luồng phiếu nhập qua API (BR-IMP-01..14). Test rollback sau mỗi ca nên không thấy được rollback
 * của chính service — các ca cần commit thật (đồng thời, rollback giữa chừng) nằm ở InboundReceiptConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class InboundReceiptApiTest {

    private static final String BASE = "/api/inbound-receipts";

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
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductSupplierRepository productSupplierRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Warehouse warehouse;
    private Supplier supplier;
    private Product pigFeed;
    private Product chickenFeed;
    private Product duckFeed;
    private Product fishFeed; // KHÔNG gán cho supplier

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("Cám test");
        categoryRepository.save(category);
        pigFeed = saveProduct(category, "Cám heo con 25kg");
        chickenFeed = saveProduct(category, "Cám gà thịt 25kg");
        duckFeed = saveProduct(category, "Cám vịt 25kg");
        fishFeed = saveProduct(category, "Cám cá 25kg");
        warehouse = saveWarehouse("Kho Hà Nội");
        supplier = saveSupplier("Công ty CP Cám Việt");
        link(supplier, pigFeed);
        link(supplier, chickenFeed);
        link(supplier, duckFeed);
    }

    // ===== Tạo phiếu nháp =====

    @Test
    @DisplayName("BR-IMP-02, BR-MON-05: tạo nháp - 201, mã PN, SL thực nhận mặc định = SL đặt, thành tiền làm tròn, chưa đổi tồn")
    void shouldCreateDraftWithoutTouchingStock() throws Exception {
        String body = body(supplier.getId(), warehouse.getId(), null,
                line(pigFeed.getId(), 3, null, "12345.50"),
                line(chickenFeed.getId(), 10, 8, "250000"));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value(matchesPattern("PN\\d{6}")))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.documentDate").value(today().toString()))
                .andExpect(jsonPath("$.data.warehouseCode").value(warehouse.getCode()))
                .andExpect(jsonPath("$.data.supplierName").value("Công ty CP Cám Việt"))
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].productCode").value(pigFeed.getCode()))
                .andExpect(jsonPath("$.data.lines[0].orderedQuantity").value(3))
                .andExpect(jsonPath("$.data.lines[0].receivedQuantity").value(3))
                .andExpect(jsonPath("$.data.lines[0].lineAmount").value(37037))   // 37.036,50 → 37.037
                .andExpect(jsonPath("$.data.lines[1].receivedQuantity").value(8))
                .andExpect(jsonPath("$.data.lines[1].lineAmount").value(2000000)) // theo SL thực nhận
                .andExpect(jsonPath("$.data.totalAmount").value(2037037))
                .andExpect(jsonPath("$.data.completedAt").doesNotExist());

        assertThat(inventoryRows()).isZero();
        assertThat(transactionRows()).isZero();
    }

    @Test
    @DisplayName("BR-IMP-08: ghi lùi ngày được")
    void shouldAcceptBackdatedDocument() throws Exception {
        LocalDate lastWeek = today().minusDays(7);

        create(body(supplier.getId(), warehouse.getId(), lastWeek, line(pigFeed.getId(), 5, null, "1000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.documentDate").value(lastWeek.toString()));
    }

    @Test
    @DisplayName("BR-IMP-08: ngày tương lai - 400, lỗi gắn vào field documentDate")
    void shouldRejectFutureDate() throws Exception {
        create(body(supplier.getId(), warehouse.getId(), today().plusDays(1), line(pigFeed.getId(), 5, null, "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("documentDate"));
    }

    @Test
    @DisplayName("BR-IMP-A05: một SP ở hai dòng - 400, chỉ ra dòng bị lặp")
    void shouldRejectDuplicateProductLine() throws Exception {
        create(body(supplier.getId(), warehouse.getId(), null,
                line(pigFeed.getId(), 5, null, "1000"),
                line(pigFeed.getId(), 2, null, "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[1].productId"));
    }

    @Test
    @DisplayName("Validation dòng hàng: SL đặt 0, SL nhận âm, giá âm, giá 3 chữ số lẻ, không có dòng - 400")
    void shouldValidateLines() throws Exception {
        create(body(supplier.getId(), warehouse.getId(), null, line(pigFeed.getId(), 0, null, "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].orderedQuantity"));
        create(body(supplier.getId(), warehouse.getId(), null, line(pigFeed.getId(), 5, -1, "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].receivedQuantity"));
        create(body(supplier.getId(), warehouse.getId(), null, line(pigFeed.getId(), 5, null, "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].unitPrice"));
        create(body(supplier.getId(), warehouse.getId(), null, line(pigFeed.getId(), 5, null, "1000.005")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].unitPrice"));
        create(body(supplier.getId(), warehouse.getId(), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines"));
    }

    @Test
    @DisplayName("BR-INV-07: số lượng 2.5 - 400 (không âm thầm cắt thành 2)")
    void shouldRejectFractionalQuantity() throws Exception {
        String body = """
                {"supplierId": %d, "warehouseId": %d,
                 "lines": [{"productId": %d, "orderedQuantity": 2.5, "unitPrice": 1000}]}
                """.formatted(supplier.getId(), warehouse.getId(), pigFeed.getId());

        create(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].orderedQuantity"));
    }

    @Test
    @DisplayName("BR-SUP-08: SP chưa gán cho NCC - 409, nêu mã SP")
    void shouldRejectProductNotLinkedToSupplier() throws Exception {
        create(body(supplier.getId(), warehouse.getId(), null,
                line(pigFeed.getId(), 5, null, "1000"),
                line(fishFeed.getId(), 5, null, "1000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.message").value(containsString(fishFeed.getCode())));
    }

    @Test
    @DisplayName("BR-SUP-08: liên kết đã gỡ (active=false) cũng bị chặn")
    void shouldRejectProductWithRemovedLink() throws Exception {
        productSupplierRepository.findById(new ProductSupplierId(supplier.getId(), pigFeed.getId()))
                .orElseThrow().setActive(false);

        create(body(supplier.getId(), warehouse.getId(), null, line(pigFeed.getId(), 5, null, "1000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("BR-IMP-A04: kho / NCC / SP ngừng hoạt động - 409")
    void shouldRejectInactiveReferences() throws Exception {
        String body = body(supplier.getId(), warehouse.getId(), null, line(pigFeed.getId(), 5, null, "1000"));

        warehouse.setActive(false);
        warehouseRepository.saveAndFlush(warehouse);
        create(body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Kho")));
        warehouse.setActive(true);
        warehouseRepository.saveAndFlush(warehouse);

        supplier.setActive(false);
        supplierRepository.saveAndFlush(supplier);
        create(body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("nhà cung cấp")));
        supplier.setActive(true);
        supplierRepository.saveAndFlush(supplier);

        pigFeed.setActive(false);
        productRepository.saveAndFlush(pigFeed);
        create(body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("ngừng kinh doanh")));
    }

    @Test
    @DisplayName("Kho / NCC / SP không tồn tại - 404")
    void shouldReturnNotFoundForUnknownReferences() throws Exception {
        create(body(supplier.getId(), 999_999L, null, line(pigFeed.getId(), 5, null, "1000")))
                .andExpect(status().isNotFound());
        create(body(999_999L, warehouse.getId(), null, line(pigFeed.getId(), 5, null, "1000")))
                .andExpect(status().isNotFound());
        create(body(supplier.getId(), warehouse.getId(), null, line(999_999L, 5, null, "1000")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // ===== Sửa phiếu nháp =====

    @Test
    @DisplayName("BR-IMP-03: sửa nháp khi hàng về - sửa SL thực nhận, bỏ dòng, thêm dòng; dòng giữ lại không đổi id")
    void shouldUpdateDraftLinesInPlace() throws Exception {
        JsonNode draft = createDraft(
                line(pigFeed.getId(), 10, null, "1000"),
                line(chickenFeed.getId(), 5, null, "2000"));
        long id = draft.get("id").asLong();
        long pigLineId = draft.get("lines").get(0).get("id").asLong();

        mockMvc.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(supplier.getId(), warehouse.getId(), null,
                                line(pigFeed.getId(), 10, 9, "1000"),
                                line(duckFeed.getId(), 4, null, "3000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].id").value(pigLineId))
                .andExpect(jsonPath("$.data.lines[0].orderedQuantity").value(10))
                .andExpect(jsonPath("$.data.lines[0].receivedQuantity").value(9))
                .andExpect(jsonPath("$.data.lines[1].productId").value(duckFeed.getId()))
                .andExpect(jsonPath("$.data.totalAmount").value(9 * 1000 + 4 * 3000));

        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stock_document_item WHERE document_id = ?",
                Integer.class, id)).isEqualTo(2);
        assertThat(inventoryRows()).isZero();
    }

    // ===== Hoàn tất =====

    @Test
    @DisplayName("BR-IMP-04: hoàn tất - tồn tăng theo SL thực nhận, mỗi dòng một giao dịch IN, phiếu chuyển Hoàn tất")
    void shouldCompleteAndIncreaseStock() throws Exception {
        long id = createDraft(
                line(pigFeed.getId(), 10, 9, "1000"),
                line(chickenFeed.getId(), 5, null, "2000")).get("id").asLong();

        complete(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").exists());

        assertThat(inventory(pigFeed)).isEqualTo(9);
        assertThat(inventory(chickenFeed)).isEqualTo(5);
        List<Map<String, Object>> transactions = transactionsOf(id);
        assertThat(transactions).hasSize(2);
        assertThat(transactions).allSatisfy(tx -> {
            assertThat(tx.get("type")).isEqualTo("IN");
            assertThat(tx.get("quantity_before")).isEqualTo(0);
            assertThat(tx.get("reversal_of_id")).isNull();
        });
    }

    @Test
    @DisplayName("Hoàn tất phiếu thứ hai cộng dồn vào tồn có sẵn; quantity_before/after đúng")
    void shouldAddToExistingStock() throws Exception {
        long first = createDraft(line(pigFeed.getId(), 10, null, "1000")).get("id").asLong();
        long second = createDraft(line(pigFeed.getId(), 7, null, "1000")).get("id").asLong();

        complete(first).andExpect(status().isOk());
        complete(second).andExpect(status().isOk());

        assertThat(inventory(pigFeed)).isEqualTo(17);
        Map<String, Object> tx = transactionsOf(second).get(0);
        assertThat(tx.get("quantity_before")).isEqualTo(10);
        assertThat(tx.get("quantity_after")).isEqualTo(17);
    }

    @Test
    @DisplayName("BR-IMP-A06: dòng SL thực nhận 0 không sinh giao dịch; mọi dòng = 0 thì không hoàn tất được")
    void shouldSkipZeroLinesAndRejectAllZero() throws Exception {
        long partial = createDraft(
                line(pigFeed.getId(), 10, 0, "1000"),
                line(chickenFeed.getId(), 5, null, "2000")).get("id").asLong();
        complete(partial).andExpect(status().isOk());
        assertThat(transactionsOf(partial)).hasSize(1);
        assertThat(inventory(pigFeed)).isNull();

        long allZero = createDraft(line(duckFeed.getId(), 10, 0, "1000")).get("id").asLong();
        complete(allZero)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
        assertThat(statusOf(allZero)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("BR-IMP-10: gỡ liên kết NCC-SP sau khi lưu nháp - hoàn tất bị chặn, tồn không đổi")
    void shouldRecheckLinkOnComplete() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, null, "1000")).get("id").asLong();
        mockMvc.perform(delete("/api/suppliers/{id}/products/{productId}", supplier.getId(), pigFeed.getId()))
                .andExpect(status().isNoContent());

        complete(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString(pigFeed.getCode())));
        assertThat(inventory(pigFeed)).isNull();
        assertThat(statusOf(id)).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("BR-IMP-A04: SP ngừng kinh doanh sau khi lưu nháp - hoàn tất bị chặn")
    void shouldRecheckProductOnComplete() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, null, "1000")).get("id").asLong();
        pigFeed.setActive(false);
        productRepository.saveAndFlush(pigFeed);

        complete(id).andExpect(status().isConflict());
        assertThat(inventory(pigFeed)).isNull();
    }

    // ===== Bảo vệ trạng thái (BR-IMP-11..13) =====

    @Test
    @DisplayName("BR-IMP-11, BR-IMP-13: phiếu Hoàn tất - không sửa, không sửa dòng, không hủy, không hoàn tất lại, không xóa")
    void completedReceiptShouldBeLocked() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, null, "1000")).get("id").asLong();
        complete(id).andExpect(status().isOk());

        update(id, body(supplier.getId(), warehouse.getId(), null, line(pigFeed.getId(), 99, null, "1000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Chỉ phiếu Nháp mới được sửa")));
        cancel(id).andExpect(status().isConflict());
        complete(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Chỉ phiếu Nháp mới được hoàn tất")));
        mockMvc.perform(delete(BASE + "/{id}", id))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));

        // Không cộng tồn lần hai, dòng hàng giữ nguyên
        assertThat(inventory(pigFeed)).isEqualTo(10);
        assertThat(transactionsOf(id)).hasSize(1);
        mockMvc.perform(get(BASE + "/{id}", id))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.lines[0].orderedQuantity").value(10));
    }

    @Test
    @DisplayName("BR-IMP-05: hủy nháp - Đã hủy, giữ phiếu và lý do; sau đó không sửa/hoàn tất/đảo/hủy lại được")
    void cancelledReceiptShouldBeFinal() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, null, "1000")).get("id").asLong();

        mockMvc.perform(post(BASE + "/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"NCC hủy đơn\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.reason").value("NCC hủy đơn"))
                .andExpect(jsonPath("$.data.cancelledAt").exists());

        update(id, body(supplier.getId(), warehouse.getId(), null, line(pigFeed.getId(), 1, null, "1000")))
                .andExpect(status().isConflict());
        complete(id).andExpect(status().isConflict());
        reverse(id, "thử").andExpect(status().isConflict());
        cancel(id).andExpect(status().isConflict());
        assertThat(inventory(pigFeed)).isNull();
    }

    @Test
    @DisplayName("Hủy nháp không cần body")
    void shouldCancelWithoutBody() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, null, "1000")).get("id").asLong();

        cancel(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.reason").doesNotExist());
    }

    @Test
    @DisplayName("BR-IMP-12: phiếu Nháp không đảo được (chỉ phiếu Hoàn tất)")
    void draftCannotBeReversed() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, null, "1000")).get("id").asLong();

        reverse(id, "nhập nhầm").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Chỉ phiếu Hoàn tất mới được đảo")));
    }

    // ===== Đảo phiếu =====

    @Test
    @DisplayName("BR-IMP-06: đảo phiếu - tồn về như cũ, mỗi giao dịch IN có một OUT trỏ về nó, phiếu Đã đảo")
    void shouldReverseCompletedReceipt() throws Exception {
        long id = createDraft(
                line(pigFeed.getId(), 10, null, "1000"),
                line(chickenFeed.getId(), 4, null, "2000")).get("id").asLong();
        complete(id);

        reverse(id, "Nhập nhầm kho")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"))
                .andExpect(jsonPath("$.data.reason").value("Nhập nhầm kho"))
                .andExpect(jsonPath("$.data.reversedAt").exists())
                .andExpect(jsonPath("$.data.completedAt").exists());

        assertThat(inventory(pigFeed)).isZero();
        assertThat(inventory(chickenFeed)).isZero();
        List<Map<String, Object>> transactions = transactionsOf(id);
        assertThat(transactions).hasSize(4);
        List<Map<String, Object>> reversals = transactions.stream().filter(tx -> "OUT".equals(tx.get("type"))).toList();
        assertThat(reversals).hasSize(2).allSatisfy(tx -> assertThat(tx.get("reversal_of_id")).isNotNull());
    }

    @Test
    @DisplayName("BR-IMP-12: phiếu chỉ đảo được một lần; phiếu Đã đảo cũng không sửa/hoàn tất/hủy được")
    void reversedReceiptShouldBeFinal() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, null, "1000")).get("id").asLong();
        complete(id);
        reverse(id, "Nhập nhầm").andExpect(status().isOk());

        reverse(id, "Đảo lần hai")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("chỉ được đảo một lần")));
        update(id, body(supplier.getId(), warehouse.getId(), null, line(pigFeed.getId(), 1, null, "1000")))
                .andExpect(status().isConflict());
        complete(id).andExpect(status().isConflict());
        cancel(id).andExpect(status().isConflict());

        assertThat(inventory(pigFeed)).isZero();
        assertThat(transactionsOf(id)).hasSize(2);
    }

    @Test
    @DisplayName("BR-IMP-06: đảo phiếu khi tồn không đủ (hàng đã xuất bớt) - 409, phiếu vẫn Hoàn tất")
    void shouldRejectReverseWhenStockInsufficient() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, null, "1000")).get("id").asLong();
        complete(id);
        // Giả lập đã xuất 7 bao (module xuất kho chưa có)
        jdbcTemplate.update("UPDATE inventory SET quantity = 3 WHERE product_id = ?", pigFeed.getId());

        reverse(id, "Nhập nhầm")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Không đủ tồn")))
                .andExpect(jsonPath("$.error.message").value(containsString("còn 3, cần trừ 10")));
        assertThat(inventory(pigFeed)).isEqualTo(3);
    }

    @Test
    @DisplayName("Đảo phiếu thiếu lý do - 400")
    void shouldRequireReverseReason() throws Exception {
        long id = createDraft(line(pigFeed.getId(), 10, null, "1000")).get("id").asLong();
        complete(id);

        reverse(id, " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("reason"));
    }

    // ===== Đọc, lọc =====

    @Test
    @DisplayName("GET chi tiết phiếu không tồn tại - 404")
    void shouldReturnNotFound() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        complete(999_999L).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Lọc danh sách theo trạng thái, kho, NCC, khoảng ngày chứng từ, mã; mặc định mới nhất trước")
    void shouldFilterList() throws Exception {
        Warehouse other = saveWarehouse("Kho Hải Phòng");
        long old = createDraft(today().minusDays(10), warehouse, line(pigFeed.getId(), 1, null, "1000"))
                .get("id").asLong();
        JsonNode completed = createDraft(today().minusDays(2), warehouse, line(pigFeed.getId(), 1, null, "1000"));
        complete(completed.get("id").asLong());
        long inOtherWarehouse = createDraft(today(), other, line(pigFeed.getId(), 1, null, "1000"))
                .get("id").asLong();

        mockMvc.perform(get(BASE))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(3))
                .andExpect(jsonPath("$.data.items[0].id").value(inOtherWarehouse))
                .andExpect(jsonPath("$.data.items[2].id").value(old))
                .andExpect(jsonPath("$.data.items[0].lines").doesNotExist());
        mockMvc.perform(get(BASE).param("status", "COMPLETED"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"));
        mockMvc.perform(get(BASE).param("warehouseId", other.getId().toString()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].warehouseName").value("Kho Hải Phòng"));
        mockMvc.perform(get(BASE).param("supplierId", supplier.getId().toString()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(3));
        mockMvc.perform(get(BASE)
                        .param("documentDateFrom", today().minusDays(5).toString())
                        .param("documentDateTo", today().minusDays(2).toString()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(completed.get("id").asLong()));
        String code = completed.get("code").asText();
        mockMvc.perform(get(BASE).param("keyword", code.toLowerCase()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value(code));
        mockMvc.perform(get(BASE).param("status", "APPROVED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("status"));
    }

    // ===== Helpers =====

    private JsonNode createDraft(String... lines) throws Exception {
        return createDraft(null, warehouse, lines);
    }

    private JsonNode createDraft(LocalDate date, Warehouse target, String... lines) throws Exception {
        String response = create(body(supplier.getId(), target.getId(), date, lines))
                .andExpect(status().isCreated())
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

    private ResultActions cancel(long id) throws Exception {
        return mockMvc.perform(post(BASE + "/{id}/cancel", id));
    }

    private ResultActions reverse(long id, String reason) throws Exception {
        return mockMvc.perform(post(BASE + "/{id}/reverse", id).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private static String body(Long supplierId, Long warehouseId, LocalDate date, String... lines) {
        String dateField = date == null ? "" : "\"documentDate\": \"" + date + "\", ";
        return "{\"supplierId\": " + supplierId + ", \"warehouseId\": " + warehouseId + ", " + dateField
                + "\"lines\": [" + String.join(", ", lines) + "]}";
    }

    private static String line(Long productId, int ordered, Integer received, String unitPrice) {
        String receivedField = received == null ? "" : ", \"receivedQuantity\": " + received;
        return "{\"productId\": " + productId + ", \"orderedQuantity\": " + ordered + receivedField
                + ", \"unitPrice\": " + unitPrice + "}";
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }

    // JdbcTemplate đọc thẳng DB → flush trước để thấy thay đổi Hibernate còn giữ trong bộ nhớ
    private Integer inventory(Product product) {
        entityManager.flush();
        return jdbcTemplate.query("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, product.getId(), warehouse.getId());
    }

    private int inventoryRows() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM inventory", Integer.class);
    }

    private int transactionRows() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM stock_transaction", Integer.class);
    }

    private List<Map<String, Object>> transactionsOf(long documentId) {
        entityManager.flush();
        return jdbcTemplate.queryForList("SELECT t.* FROM stock_transaction t"
                + " JOIN stock_document_item i ON i.id = t.document_item_id WHERE i.document_id = ? ORDER BY t.id",
                documentId);
    }

    private String statusOf(long documentId) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT status FROM stock_document WHERE id = ?", String.class, documentId);
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

    private Supplier saveSupplier(String name) {
        Supplier s = new Supplier();
        s.setName(name);
        return supplierRepository.save(s);
    }

    private void link(Supplier s, Product p) {
        productSupplierRepository.save(new ProductSupplier(s, p));
    }
}
