package com.grainwarehouse.transfer;

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
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phiếu điều chuyển kho qua API (BR-TRF-01..13). Test rollback sau mỗi ca; các ca cần commit thật (đồng thời,
 * deadlock) nằm ở TransferConcurrencyTest.
 *
 * <p>Kho Hà Nội được tạo trước nên có id NHỎ hơn kho Bắc Ninh. Vì với mỗi SP, kho có id nhỏ hơn được đụng trước (BR-TRF-04):
 * Hà Nội → Bắc Ninh = trừ nguồn trước, cộng đích sau; Bắc Ninh → Hà Nội = cộng đích trước, trừ nguồn sau.
 * Hai chiều đều có ca kiểm số; rollback của cả hai chiều kiểm ở TransferConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransferApiTest {

    private static final String BASE = "/api/transfers";

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
    private WarehouseRepository warehouseRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Category category;
    private Warehouse hanoi;     // id nhỏ hơn
    private Warehouse bacNinh;   // id lớn hơn
    private Product cornFeed;    // cám A
    private Product pigFeed;     // cám B
    private int customerSeq;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Cám điều chuyển test");
        categoryRepository.save(category);
        cornFeed = saveProduct("Cám A 25kg");
        pigFeed = saveProduct("Cám B 25kg");
        hanoi = saveWarehouse("Kho Hà Nội");
        bacNinh = saveWarehouse("Kho Bắc Ninh");
        assertThat(hanoi.getId()).isLessThan(bacNinh.getId());
    }

    // ===== Lưu nháp =====

    @Test
    @DisplayName("BR-TRF-05..08: tạo nháp - 201, mã DC, hai kho, ghi người lập; chưa đổi tồn, chưa có lịch sử")
    void shouldCreateDraftWithoutTouchingStock() throws Exception {
        seedStock(hanoi, cornFeed, 100);
        int before = transactionRows();

        create(body(hanoi, bacNinh, null, line(cornFeed, 30), line(pigFeed, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(matchesPattern("DC\\d{6}")))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.documentDate").value(today().toString()))
                .andExpect(jsonPath("$.data.sourceWarehouseName").value("Kho Hà Nội"))
                .andExpect(jsonPath("$.data.targetWarehouseName").value("Kho Bắc Ninh"))
                .andExpect(jsonPath("$.data.createdBy").exists())
                .andExpect(jsonPath("$.data.completedBy").doesNotExist())
                .andExpect(jsonPath("$.data.lineCount").value(2))
                .andExpect(jsonPath("$.data.totalQuantity").value(35))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(30))
                .andExpect(jsonPath("$.data.lines[0].netWeightKg").value(25));

        // Nháp không giữ chỗ tồn: cám B chưa có ở Hà Nội vẫn lưu nháp được
        assertThat(inventory(cornFeed, hanoi)).isEqualTo(100);
        assertThat(inventory(cornFeed, bacNinh)).isNull();
        assertThat(transactionRows()).isEqualTo(before);
    }

    @Test
    @DisplayName("BR-TRF-05: kho đích trùng kho nguồn, SL 0/âm/lẻ, trùng SP, không có dòng, thiếu kho - 400 kèm field")
    void shouldValidateRequest() throws Exception {
        create(body(hanoi, hanoi, null, line(cornFeed, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("targetWarehouseId"));
        create(body(hanoi, bacNinh, null, line(cornFeed, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].quantity"));
        create(body(hanoi, bacNinh, null, line(cornFeed, -5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].quantity"));
        create("{\"sourceWarehouseId\": " + hanoi.getId() + ", \"targetWarehouseId\": " + bacNinh.getId()
                + ", \"lines\": [{\"productId\": " + cornFeed.getId() + ", \"quantity\": 2.5}]}")
                .andExpect(status().isBadRequest());
        create(body(hanoi, bacNinh, null, line(cornFeed, 5), line(cornFeed, 7)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[1].productId"));
        create(body(hanoi, bacNinh, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines"));
        create("{\"sourceWarehouseId\": " + hanoi.getId() + ", \"lines\": [" + line(cornFeed, 5) + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("targetWarehouseId"));
    }

    @Test
    @DisplayName("BR-TRF-10: ngày tương lai - 400; ngày lùi được")
    void shouldRejectFutureDate() throws Exception {
        create(body(hanoi, bacNinh, today().plusDays(1), line(cornFeed, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("documentDate"));
        create(body(hanoi, bacNinh, LocalDate.of(2026, 1, 1), line(cornFeed, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.documentDate").value("2026-01-01"));
    }

    @Test
    @DisplayName("BR-TRF-09: kho đích ngừng hoạt động - 409; kho / SP không tồn tại - 404")
    void shouldRejectInactiveTargetOrUnknownReferences() throws Exception {
        Warehouse closed = saveWarehouse("Kho đóng cửa");
        closed.setActive(false);
        create(body(hanoi, closed, null, line(cornFeed, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("ngừng hoạt động")));

        create("{\"sourceWarehouseId\": 999999, \"targetWarehouseId\": " + bacNinh.getId()
                + ", \"lines\": [" + line(cornFeed, 1) + "]}")
                .andExpect(status().isNotFound());
        create("{\"sourceWarehouseId\": " + hanoi.getId() + ", \"targetWarehouseId\": " + bacNinh.getId()
                + ", \"lines\": [{\"productId\": 999999, \"quantity\": 1}]}")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-TRF-09: kho NGUỒN đã ngừng và SP đã ngừng kinh doanh vẫn chuyển đi được - rút hàng khỏi kho đóng cửa")
    void shouldTransferOutOfInactiveSourceAndInactiveProduct() throws Exception {
        seedStock(hanoi, cornFeed, 40);
        hanoi.setActive(false);
        cornFeed.setActive(false);
        entityManager.flush();

        complete(createDraft(hanoi, bacNinh, line(cornFeed, 40))).andExpect(status().isOk());

        assertThat(inventory(cornFeed, hanoi)).isZero();
        assertThat(inventory(cornFeed, bacNinh)).isEqualTo(40);
    }

    @Test
    @DisplayName("BR-TRF-09: kho đích bị ngừng SAU khi lưu nháp - hoàn tất bị từ chối 409, tồn không đổi")
    void shouldRecheckTargetOnComplete() throws Exception {
        seedStock(hanoi, cornFeed, 40);
        long id = createDraft(hanoi, bacNinh, line(cornFeed, 10));
        bacNinh.setActive(false);
        entityManager.flush();

        complete(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("ngừng hoạt động")));

        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(inventory(cornFeed, hanoi)).isEqualTo(40);
    }

    @Test
    @DisplayName("BR-TRF-08: sửa nháp (đổi kho đích, đổi SL, bỏ dòng, thêm dòng) rồi hoàn tất - tồn theo bản đã sửa")
    void shouldEditDraftBeforeCompleting() throws Exception {
        Warehouse haiPhong = saveWarehouse("Kho Hải Phòng");
        seedStock(hanoi, cornFeed, 100);
        seedStock(hanoi, pigFeed, 50);
        long id = createDraft(hanoi, bacNinh, line(cornFeed, 10));

        update(id, body(hanoi, haiPhong, null, line(pigFeed, 20), line(cornFeed, 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetWarehouseName").value("Kho Hải Phòng"))
                .andExpect(jsonPath("$.data.lineCount").value(2));
        update(id, body(hanoi, haiPhong, null, line(pigFeed, 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineCount").value(1));
        complete(id).andExpect(status().isOk());

        assertThat(inventory(pigFeed, hanoi)).isEqualTo(30);
        assertThat(inventory(pigFeed, haiPhong)).isEqualTo(20);
        assertThat(inventory(cornFeed, hanoi)).isEqualTo(100);
        assertThat(inventory(cornFeed, bacNinh)).isNull();
    }

    // ===== Hoàn tất =====

    @Test
    @DisplayName("BR-TRF-01: hoàn tất - nguồn giảm, đích tăng đúng số; mỗi dòng 1 OUT ở nguồn + 1 IN ở đích cùng trỏ một dòng phiếu")
    void shouldMoveStockAndWriteBothTransactions() throws Exception {
        seedStock(hanoi, cornFeed, 100);
        seedStock(hanoi, pigFeed, 50);
        seedStock(bacNinh, cornFeed, 7);
        long id = createDraft(hanoi, bacNinh, line(cornFeed, 30), line(pigFeed, 50));

        complete(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").exists())
                .andExpect(jsonPath("$.data.completedBy").exists());

        assertThat(inventory(cornFeed, hanoi)).isEqualTo(70);
        assertThat(inventory(cornFeed, bacNinh)).isEqualTo(37);
        assertThat(inventory(pigFeed, hanoi)).isZero();
        assertThat(inventory(pigFeed, bacNinh)).isEqualTo(50);

        List<Map<String, Object>> transactions = transactionsOf(id);
        assertThat(transactions).hasSize(4);
        Map<String, Object> out = find(transactions, cornFeed, hanoi);
        Map<String, Object> in = find(transactions, cornFeed, bacNinh);
        assertThat(out).containsEntry("type", "OUT").containsEntry("quantity", 30)
                .containsEntry("quantity_before", 100).containsEntry("quantity_after", 70);
        assertThat(in).containsEntry("type", "IN").containsEntry("quantity", 30)
                .containsEntry("quantity_before", 7).containsEntry("quantity_after", 37);
        assertThat(out.get("document_item_id")).isEqualTo(in.get("document_item_id"));
        // Tổng tồn của hệ thống không đổi: điều chuyển không sinh ra hay làm mất hàng
        assertThat(totalStock(cornFeed)).isEqualTo(107);
    }

    // Rollback khi phiếu NHIỀU bước thất bại giữa chừng (BR-TRF-02, 03) được kiểm ở TransferConcurrencyTest: ở đây
    // cả test nằm trong một transaction, phần đã trừ trước khi lỗi vẫn còn nhìn thấy nên không chứng minh được gì.
    @Test
    @DisplayName("BR-INV-01: kho nguồn không đủ tồn - 409 báo rõ còn bao nhiêu, phiếu vẫn Nháp")
    void shouldRejectWhenSourceIsShort() throws Exception {
        seedStock(hanoi, cornFeed, 10);
        long id = createDraft(hanoi, bacNinh, line(cornFeed, 11));

        complete(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Không đủ tồn")))
                .andExpect(jsonPath("$.error.message").value(containsString("còn 10")));

        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(inventory(cornFeed, hanoi)).isEqualTo(10);
    }

    @Test
    @DisplayName("BR-TRF-01: chuyển ngược chiều id (kho đích id nhỏ hơn) vẫn đúng số và đủ lịch sử")
    void shouldMoveStockTowardsLowerId() throws Exception {
        seedStock(bacNinh, cornFeed, 20);
        long id = createDraft(bacNinh, hanoi, line(cornFeed, 20));

        complete(id).andExpect(status().isOk());

        assertThat(inventory(cornFeed, bacNinh)).isZero();
        assertThat(inventory(cornFeed, hanoi)).isEqualTo(20);
        assertThat(transactionsOf(id)).hasSize(2);
    }

    @Test
    @DisplayName("BR-TRF-08: hoàn tất lần hai / sửa / hủy phiếu đã hoàn tất - 409, tồn chỉ đổi một lần")
    void shouldRejectChangesAfterComplete() throws Exception {
        seedStock(hanoi, cornFeed, 100);
        long id = createDraft(hanoi, bacNinh, line(cornFeed, 30));
        complete(id).andExpect(status().isOk());

        complete(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Hoàn tất")));
        update(id, body(hanoi, bacNinh, null, line(cornFeed, 60))).andExpect(status().isConflict());
        cancel(id).andExpect(status().isConflict());

        assertThat(inventory(cornFeed, hanoi)).isEqualTo(70);
        assertThat(inventory(cornFeed, bacNinh)).isEqualTo(30);
        assertThat(transactionsOf(id)).hasSize(2);
    }

    // ===== Hủy, đảo =====

    @Test
    @DisplayName("BR-TRF-08: hủy nháp - Đã hủy, không đổi tồn; hoàn tất phiếu đã hủy - 409")
    void shouldCancelDraft() throws Exception {
        seedStock(hanoi, cornFeed, 100);
        long id = createDraft(hanoi, bacNinh, line(cornFeed, 30));

        cancel(id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        complete(id).andExpect(status().isConflict());

        assertThat(inventory(cornFeed, hanoi)).isEqualTo(100);
    }

    @Test
    @DisplayName("BR-TRF-12: đảo - hàng về lại kho nguồn, mỗi giao dịch gốc có một giao dịch đảo trỏ về; đảo lần hai - 409")
    void shouldReverse() throws Exception {
        seedStock(hanoi, cornFeed, 100);
        long id = createDraft(hanoi, bacNinh, line(cornFeed, 30));
        complete(id).andExpect(status().isOk());

        reverse(id, "Chuyển nhầm kho")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"))
                .andExpect(jsonPath("$.data.reason").value("Chuyển nhầm kho"));

        assertThat(inventory(cornFeed, hanoi)).isEqualTo(100);
        assertThat(inventory(cornFeed, bacNinh)).isZero();
        List<Map<String, Object>> transactions = transactionsOf(id);
        assertThat(transactions).hasSize(4);
        List<Object> originalIds = transactions.stream().filter(t -> t.get("reversal_of_id") == null)
                .map(t -> t.get("id")).toList();
        List<Object> reversedIds = transactions.stream().filter(t -> t.get("reversal_of_id") != null)
                .map(t -> t.get("reversal_of_id")).toList();
        assertThat(reversedIds).containsExactlyInAnyOrderElementsOf(originalIds);

        reverse(id, "Lần nữa").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("chỉ được đảo một lần")));
    }

    @Test
    @DisplayName("BR-TRF-12: kho đích đã bán bớt, không đủ để trả về - 409, phiếu vẫn Hoàn tất")
    void shouldRejectReverseWhenTargetSold() throws Exception {
        seedStock(hanoi, cornFeed, 100);
        long id = createDraft(hanoi, bacNinh, line(cornFeed, 30));
        complete(id).andExpect(status().isOk());
        completeSale(bacNinh, cornFeed, 5).andExpect(status().isOk());

        reverse(id, "Chuyển nhầm")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Không đủ tồn")));

        assertThat(statusOf(id)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Đảo cần lý do - 400; đảo phiếu nháp - 409")
    void reverseShouldRequireReasonAndCompletedStatus() throws Exception {
        seedStock(hanoi, cornFeed, 100);
        long id = createDraft(hanoi, bacNinh, line(cornFeed, 30));

        reverse(id, "Chưa hoàn tất").andExpect(status().isConflict());
        complete(id).andExpect(status().isOk());
        reverse(id, " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("reason"));
    }

    // ===== Đọc =====

    @Test
    @DisplayName("Danh sách: lọc kho nguồn / kho đích / trạng thái, kèm số dòng và tổng SL; chỉ phiếu điều chuyển")
    void listShouldFilterAndSummarize() throws Exception {
        seedStock(hanoi, cornFeed, 100);   // phiếu tồn đầu kỳ không được lẫn vào danh sách
        seedStock(hanoi, pigFeed, 50);
        long first = createDraft(hanoi, bacNinh, line(cornFeed, 30), line(pigFeed, 20));
        createDraft(bacNinh, hanoi, line(cornFeed, 5));
        complete(first).andExpect(status().isOk());

        mockMvc.perform(get(BASE).param("sourceWarehouseId", hanoi.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].lineCount").value(2))
                .andExpect(jsonPath("$.data.items[0].totalQuantity").value(50))
                .andExpect(jsonPath("$.data.items[0].targetWarehouseName").value("Kho Bắc Ninh"))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"));
        mockMvc.perform(get(BASE).param("targetWarehouseId", hanoi.getId().toString()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].sourceWarehouseName").value("Kho Bắc Ninh"));
        mockMvc.perform(get(BASE).param("status", "DRAFT"))
                .andExpect(jsonPath("$.data.items.length()").value(1));
        mockMvc.perform(get(BASE)).andExpect(jsonPath("$.data.pagination.totalElements").value(2));
        mockMvc.perform(get(BASE + "/{id}", first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines.length()").value(2));
        mockMvc.perform(get(BASE + "/999999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Phiếu loại khác không mở được qua /api/transfers - 404")
    void shouldNotExposeOtherDocumentTypes() throws Exception {
        long openingId = seedStock(hanoi, cornFeed, 100);

        mockMvc.perform(get(BASE + "/{id}", openingId)).andExpect(status().isNotFound());
        reverse(openingId, "Thử đảo nhầm đường").andExpect(status().isNotFound());
        assertThat(inventory(cornFeed, hanoi)).isEqualTo(100);
    }

    @Test
    @DisplayName("Lịch sử kho: hai dòng TRANSFER, mỗi dòng ghi kho ở đầu kia của lần chuyển")
    void stockHistoryShouldShowCounterpartWarehouse() throws Exception {
        seedStock(hanoi, cornFeed, 100);
        complete(createDraft(hanoi, bacNinh, line(cornFeed, 30))).andExpect(status().isOk());

        mockMvc.perform(get("/api/inventory/transactions").param("documentType", "TRANSFER")
                        .param("warehouseId", hanoi.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("OUT"))
                .andExpect(jsonPath("$.data.items[0].counterpartWarehouseName").value("Kho Bắc Ninh"));
        mockMvc.perform(get("/api/inventory/transactions").param("documentType", "TRANSFER")
                        .param("warehouseId", bacNinh.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].type").value("IN"))
                .andExpect(jsonPath("$.data.items[0].counterpartWarehouseName").value("Kho Hà Nội"));
        // Loại chứng từ khác không có trường này (null bị bỏ khỏi JSON)
        mockMvc.perform(get("/api/inventory/transactions").param("documentType", "OPENING_STOCK")
                        .param("warehouseId", hanoi.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].counterpartWarehouseName").doesNotExist());
    }

    @Test
    @DisplayName("BR-TRF-13: nhập-xuất-tồn - lọc một kho thì điều chuyển là nhập/xuất của kho đó; mọi kho thì không tính")
    void movementReportShouldTreatTransferAsInternal() throws Exception {
        seedStock(hanoi, cornFeed, 100);
        complete(createDraft(hanoi, bacNinh, line(cornFeed, 30))).andExpect(status().isOk());

        movement(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath(row("inboundQuantity")).value(100))
                .andExpect(jsonPath(row("outboundQuantity")).value(0))
                .andExpect(jsonPath(row("closingQuantity")).value(100));
        movement(hanoi)
                .andExpect(jsonPath(row("inboundQuantity")).value(100))
                .andExpect(jsonPath(row("outboundQuantity")).value(30))
                .andExpect(jsonPath(row("closingQuantity")).value(70));
        movement(bacNinh)
                .andExpect(jsonPath(row("inboundQuantity")).value(30))
                .andExpect(jsonPath(row("outboundQuantity")).value(0))
                .andExpect(jsonPath(row("closingQuantity")).value(30));
    }

    // ===== Ràng buộc ở DB =====

    @Test
    @DisplayName("CHECK ở DB: phiếu điều chuyển phải có kho đích")
    void databaseShouldRequireTargetWarehouse() {
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, warehouse_id,"
                + " document_date, tracks_debt) VALUES ('DC-X1', 'TRANSFER', ?, CURRENT_DATE, FALSE)", hanoi.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_target_warehouse");
    }

    // Tách riêng từng ca: sau một lỗi SQL, transaction của test bị hủy, câu lệnh thứ hai không chạy được
    @Test
    @DisplayName("CHECK ở DB: kho đích phải khác kho nguồn")
    void databaseShouldRejectSameWarehouse() {
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, warehouse_id,"
                + " target_warehouse_id, document_date, tracks_debt) VALUES ('DC-X2', 'TRANSFER', ?, ?, CURRENT_DATE,"
                + " FALSE)", hanoi.getId(), hanoi.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_transfer_distinct");
    }

    @Test
    @DisplayName("CHECK ở DB: phiếu điều chuyển không theo dõi công nợ")
    void databaseShouldRejectTransferWithDebt() {
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, warehouse_id,"
                + " target_warehouse_id, document_date, tracks_debt) VALUES ('DC-X3', 'TRANSFER', ?, ?, CURRENT_DATE,"
                + " TRUE)", hanoi.getId(), bacNinh.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_transfer");
    }

    @Test
    @DisplayName("CHECK ở DB: loại chứng từ khác không được có kho đích")
    void databaseShouldRejectTargetOnOtherTypes() {
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, warehouse_id,"
                + " target_warehouse_id, document_date, tracks_debt) VALUES ('DTK-X9', 'OPENING_STOCK', ?, ?,"
                + " CURRENT_DATE, FALSE)", hanoi.getId(), bacNinh.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_target_warehouse");
    }

    // ===== Helpers =====

    private long createDraft(Warehouse source, Warehouse target, String... lines) throws Exception {
        return readId(create(body(source, target, null, lines)).andExpect(status().isCreated()));
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

    /** Nhập – xuất – tồn hôm nay của cám A; warehouse null = mọi kho. */
    private ResultActions movement(Warehouse warehouse) throws Exception {
        var request = get("/api/reports/stock-movement").param("from", today().toString())
                .param("to", today().toString()).param("categoryId", category.getId().toString());
        if (warehouse != null) {
            request.param("warehouseId", warehouse.getId().toString());
        }
        return mockMvc.perform(request);
    }

    private String row(String field) {
        return "$.data.rows[?(@.productId == %d)].%s".formatted(cornFeed.getId(), field);
    }

    /** Cho kho có sẵn hàng bằng phiếu tồn đầu kỳ qua API thật. Trả id phiếu đầu kỳ. */
    private long seedStock(Warehouse warehouse, Product product, int quantity) throws Exception {
        long id = readId(mockMvc.perform(post("/api/opening-stocks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\": " + warehouse.getId() + ", \"lines\": [{\"productId\": "
                                + product.getId() + ", \"quantity\": " + quantity + "}]}"))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/opening-stocks/{id}/complete", id)).andExpect(status().isOk());
        return id;
    }

    /** Phiếu bán qua API thật; trả kết quả bước hoàn tất. */
    private ResultActions completeSale(Warehouse warehouse, Product product, int quantity) throws Exception {
        customerSeq++;
        Customer customer = new Customer();
        customer.setName("Khách điều chuyển " + customerSeq);
        customer.setType(CustomerType.DEALER);
        customer.setPhone("08%08d".formatted(customerSeq));
        customerRepository.save(customer);
        long id = readId(mockMvc.perform(post("/api/sales-orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": " + customer.getId() + ", \"warehouseId\": " + warehouse.getId()
                                + ", \"lines\": [{\"productId\": " + product.getId() + ", \"quantity\": " + quantity
                                + ", \"unitPrice\": 1000}]}"))
                .andExpect(status().isCreated()));
        return mockMvc.perform(post("/api/sales-orders/{id}/complete", id));
    }

    private long readId(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private static String body(Warehouse source, Warehouse target, LocalDate date, String... lines) {
        String dateField = date == null ? "" : "\"documentDate\": \"" + date + "\", ";
        return "{\"sourceWarehouseId\": " + source.getId() + ", \"targetWarehouseId\": " + target.getId() + ", "
                + dateField + "\"lines\": [" + String.join(", ", lines) + "]}";
    }

    private static String line(Product product, int quantity) {
        return "{\"productId\": " + product.getId() + ", \"quantity\": " + quantity + "}";
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }

    private static Map<String, Object> find(List<Map<String, Object>> transactions, Product product, Warehouse warehouse) {
        return transactions.stream()
                .filter(t -> product.getId().equals(t.get("product_id")) && warehouse.getId().equals(t.get("warehouse_id")))
                .findFirst().orElseThrow();
    }

    // JdbcTemplate đọc thẳng DB → flush trước để thấy thay đổi Hibernate còn giữ trong bộ nhớ
    private Integer inventory(Product product, Warehouse warehouse) {
        entityManager.flush();
        return jdbcTemplate.query("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, product.getId(), warehouse.getId());
    }

    private Integer totalStock(Product product) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT SUM(quantity)::int FROM inventory WHERE product_id = ?",
                Integer.class, product.getId());
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

    private Product saveProduct(String name) {
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
}
