package com.grainwarehouse.opening;

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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phiếu tồn đầu kỳ qua API (BR-OPN-01..10). Test rollback sau mỗi ca; các ca cần commit thật (đồng thời)
 * nằm ở OpeningStockConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OpeningStockApiTest {

    private static final String BASE = "/api/opening-stocks";

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
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductSupplierRepository productSupplierRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Category category;
    private Warehouse hanoi;
    private Warehouse bacNinh;
    private Product cornFeed;   // cám A
    private Product pigFeed;    // cám B
    private int customerSeq;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Cám test");
        categoryRepository.save(category);
        cornFeed = saveProduct("Cám A 25kg");
        pigFeed = saveProduct("Cám B 25kg");
        hanoi = saveWarehouse("Kho Hà Nội");
        bacNinh = saveWarehouse("Kho Bắc Ninh");
    }

    // ===== Lưu nháp =====

    @Test
    @DisplayName("BR-OPN-01, 03: tạo nháp - 201, mã DTK, ghi người lập, chưa đổi tồn, chưa có lịch sử")
    void shouldCreateDraftWithoutTouchingStock() throws Exception {
        create(body(hanoi, null, line(cornFeed, 500, "310000"), line(pigFeed, 300, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(matchesPattern("DTK\\d{6}")))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.documentDate").value(today().toString()))
                .andExpect(jsonPath("$.data.warehouseName").value("Kho Hà Nội"))
                .andExpect(jsonPath("$.data.createdBy").exists())
                .andExpect(jsonPath("$.data.completedBy").doesNotExist())
                .andExpect(jsonPath("$.data.lineCount").value(2))
                .andExpect(jsonPath("$.data.totalQuantity").value(800))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(500))
                .andExpect(jsonPath("$.data.lines[0].lineAmount").value(155000000))
                // BR-OPN-10: không gửi đơn giá = 0, chỉ tham khảo
                .andExpect(jsonPath("$.data.lines[1].unitPrice").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(155000000));

        assertThat(inventory(cornFeed, hanoi)).isNull();
        assertThat(transactionRows()).isZero();
    }

    @Test
    @DisplayName("BR-OPN-02: SL 0, SL âm, SL lẻ, thiếu SP, trùng SP trong phiếu, không có dòng - 400 kèm field")
    void shouldValidateLines() throws Exception {
        create(body(hanoi, null, line(cornFeed, 0, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].quantity"));
        create(body(hanoi, null, line(cornFeed, -5, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].quantity"));
        create("{\"warehouseId\": " + hanoi.getId() + ", \"lines\": [{\"productId\": " + cornFeed.getId()
                + ", \"quantity\": 2.5}]}")
                .andExpect(status().isBadRequest());
        create(body(hanoi, null, line(cornFeed, 5, null), line(cornFeed, 7, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[1].productId"));
        create(body(hanoi, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines"));
        create(body(hanoi, null, line(cornFeed, 5, "-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].unitPrice"));
    }

    @Test
    @DisplayName("BR-OPN-01: ngày tương lai - 400; ngày lùi được")
    void shouldRejectFutureDate() throws Exception {
        create(body(hanoi, today().plusDays(1), line(cornFeed, 5, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("documentDate"));
        create(body(hanoi, LocalDate.of(2026, 1, 1), line(cornFeed, 5, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.documentDate").value("2026-01-01"));
    }

    @Test
    @DisplayName("BR-OPN-04: kho / SP ngừng hoạt động - 409; không tồn tại - 404")
    void shouldRejectInactiveOrUnknownReferences() throws Exception {
        Product stopped = saveProduct("Cám ngừng 25kg");
        stopped.setActive(false);
        create(body(hanoi, null, line(stopped, 5, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("ngừng kinh doanh")));

        Warehouse closed = saveWarehouse("Kho đóng cửa");
        closed.setActive(false);
        create(body(closed, null, line(cornFeed, 5, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("ngừng hoạt động")));

        create("{\"warehouseId\": 999999, \"lines\": [{\"productId\": " + cornFeed.getId() + ", \"quantity\": 1}]}")
                .andExpect(status().isNotFound());
        create("{\"warehouseId\": " + hanoi.getId() + ", \"lines\": [{\"productId\": 999999, \"quantity\": 1}]}")
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-OPN-03: sửa nháp (đổi SL, bỏ dòng, thêm dòng) rồi xác nhận - tồn theo bản đã sửa")
    void shouldEditDraftBeforeConfirming() throws Exception {
        long id = createDraft(hanoi, line(cornFeed, 100, null));

        update(id, body(hanoi, null, line(pigFeed, 300, null), line(cornFeed, 500, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineCount").value(2));
        update(id, body(hanoi, null, line(cornFeed, 500, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineCount").value(1));
        complete(id).andExpect(status().isOk());

        assertThat(inventory(cornFeed, hanoi)).isEqualTo(500);
        assertThat(inventory(pigFeed, hanoi)).isNull();
    }

    // ===== Xác nhận =====

    @Test
    @DisplayName("Xác nhận nhiều SP, nhiều kho - tồn đúng, mỗi dòng một giao dịch IN từ 0, ghi người xác nhận")
    void shouldConfirmManyProductsInManyWarehouses() throws Exception {
        long hanoiId = createDraft(hanoi, line(cornFeed, 500, null), line(pigFeed, 300, null));
        long bacNinhId = createDraft(bacNinh, line(cornFeed, 200, null));

        complete(hanoiId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").exists())
                .andExpect(jsonPath("$.data.completedBy").exists());
        complete(bacNinhId).andExpect(status().isOk());

        assertThat(inventory(cornFeed, hanoi)).isEqualTo(500);
        assertThat(inventory(pigFeed, hanoi)).isEqualTo(300);
        assertThat(inventory(cornFeed, bacNinh)).isEqualTo(200);
        List<Map<String, Object>> transactions = transactionsOf(hanoiId);
        assertThat(transactions).hasSize(2).allSatisfy(t -> {
            assertThat(t.get("type")).isEqualTo("IN");
            assertThat(t.get("quantity_before")).isEqualTo(0);
        });
    }

    @Test
    @DisplayName("BR-IMP-11: xác nhận lần hai / sửa / hủy phiếu đã xác nhận - 409, tồn chỉ cộng một lần")
    void shouldRejectChangesAfterConfirm() throws Exception {
        long id = createDraft(hanoi, line(cornFeed, 500, null));
        complete(id).andExpect(status().isOk());

        complete(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Hoàn tất")));
        update(id, body(hanoi, null, line(cornFeed, 600, null))).andExpect(status().isConflict());
        cancel(id).andExpect(status().isConflict());

        assertThat(inventory(cornFeed, hanoi)).isEqualTo(500);
        assertThat(transactionsOf(id)).hasSize(1);
    }

    @Test
    @DisplayName("BR-OPN-05: SP đã nhập đầu kỳ tại kho - phiếu thứ hai lưu nháp được nhưng xác nhận bị từ chối")
    void shouldRejectSecondOpeningForSamePair() throws Exception {
        complete(createDraft(hanoi, line(cornFeed, 500, null))).andExpect(status().isOk());

        long second = createDraft(hanoi, line(cornFeed, 50, null), line(pigFeed, 300, null));
        complete(second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString(cornFeed.getCode())))
                .andExpect(jsonPath("$.error.message").value(not(containsString(pigFeed.getCode()))));

        assertThat(statusOf(second)).isEqualTo("DRAFT");
        assertThat(inventory(cornFeed, hanoi)).isEqualTo(500);
        assertThat(inventory(pigFeed, hanoi)).isNull();   // rollback cả dòng hợp lệ

        // Bỏ dòng vi phạm thì xác nhận được: phiếu bổ sung cho SP quên khai
        update(second, body(hanoi, null, line(pigFeed, 300, null))).andExpect(status().isOk());
        complete(second).andExpect(status().isOk());
        assertThat(inventory(pigFeed, hanoi)).isEqualTo(300);
    }

    @Test
    @DisplayName("BR-OPN-05: kho đã vận hành - SP đã có phiếu nhập thì từ chối, SP chưa phát sinh vẫn được")
    void shouldRejectProductAlreadyReceived() throws Exception {
        completeInbound(hanoi, cornFeed, 40);

        complete(createDraft(hanoi, line(cornFeed, 500, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("kiểm kê")));
        // Cùng SP ở kho khác vẫn được: phạm vi là cặp (SP, kho)
        complete(createDraft(bacNinh, line(cornFeed, 200, null))).andExpect(status().isOk());
        complete(createDraft(hanoi, line(pigFeed, 300, null))).andExpect(status().isOk());

        assertThat(inventory(cornFeed, hanoi)).isEqualTo(40);
    }

    @Test
    @DisplayName("BR-OPN-05: SP đã nhập rồi bán hết (tồn về 0) vẫn coi là đã có biến động - từ chối")
    void shouldRejectProductWithHistoryEvenIfStockIsZero() throws Exception {
        completeInbound(hanoi, cornFeed, 10);
        completeSale(hanoi, cornFeed, 10).andExpect(status().isOk());
        assertThat(inventory(cornFeed, hanoi)).isZero();

        complete(createDraft(hanoi, line(cornFeed, 500, null))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Sau khởi tạo: bán trong tồn được, bán vượt tồn bị chặn; nhập thêm cộng tiếp từ số đầu kỳ")
    void stockShouldContinueFromOpening() throws Exception {
        complete(createDraft(hanoi, line(cornFeed, 500, null))).andExpect(status().isOk());

        completeSale(hanoi, cornFeed, 120).andExpect(status().isOk());
        completeSale(hanoi, cornFeed, 381)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("còn 380")));
        completeInbound(hanoi, cornFeed, 20);

        assertThat(inventory(cornFeed, hanoi)).isEqualTo(400);
    }

    // ===== Hủy, đảo =====

    @Test
    @DisplayName("BR-IMP-05: hủy nháp - Đã hủy, không đổi tồn; xác nhận phiếu đã hủy - 409")
    void shouldCancelDraft() throws Exception {
        long id = createDraft(hanoi, line(cornFeed, 500, null));

        cancel(id).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        complete(id).andExpect(status().isConflict());

        assertThat(inventory(cornFeed, hanoi)).isNull();
    }

    @Test
    @DisplayName("BR-OPN-06: đảo - tồn về 0, OUT trỏ về IN gốc; đảo xong được khởi tạo lại; đảo lần hai - 409")
    void shouldReverseAndAllowNewOpening() throws Exception {
        long id = createDraft(hanoi, line(cornFeed, 500, null));
        complete(id).andExpect(status().isOk());

        reverse(id, "Đếm nhầm, thực tế 450 bao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"))
                .andExpect(jsonPath("$.data.reason").value("Đếm nhầm, thực tế 450 bao"));
        assertThat(inventory(cornFeed, hanoi)).isZero();
        List<Map<String, Object>> transactions = transactionsOf(id);
        assertThat(transactions).hasSize(2);
        assertThat(transactions.get(1).get("reversal_of_id")).isEqualTo(transactions.get(0).get("id"));

        reverse(id, "Lần nữa").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("chỉ được đảo một lần")));

        complete(createDraft(hanoi, line(cornFeed, 450, null))).andExpect(status().isOk());
        assertThat(inventory(cornFeed, hanoi)).isEqualTo(450);
    }

    @Test
    @DisplayName("BR-OPN-06: đã bán bớt, không đủ tồn để đảo - 409, phiếu vẫn Hoàn tất")
    void shouldRejectReverseWhenStockSold() throws Exception {
        long id = createDraft(hanoi, line(cornFeed, 500, null));
        complete(id).andExpect(status().isOk());
        completeSale(hanoi, cornFeed, 100).andExpect(status().isOk());

        reverse(id, "Đếm nhầm")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Không đủ tồn")));

        assertThat(statusOf(id)).isEqualTo("COMPLETED");
        assertThat(inventory(cornFeed, hanoi)).isEqualTo(400);
    }

    @Test
    @DisplayName("Đảo cần lý do - 400")
    void reverseShouldRequireReason() throws Exception {
        long id = createDraft(hanoi, line(cornFeed, 500, null));
        complete(id).andExpect(status().isOk());

        reverse(id, " ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("reason"));
    }

    // ===== Đọc =====

    @Test
    @DisplayName("Danh sách: lọc theo kho và trạng thái, kèm số dòng và tổng SL; chỉ phiếu đầu kỳ")
    void listShouldFilterAndSummarize() throws Exception {
        long hanoiId = createDraft(hanoi, line(cornFeed, 500, null), line(pigFeed, 300, null));
        createDraft(bacNinh, line(cornFeed, 200, null));
        complete(hanoiId).andExpect(status().isOk());
        completeInbound(bacNinh, pigFeed, 5); // phiếu nhập không được lẫn vào

        mockMvc.perform(get(BASE).param("warehouseId", hanoi.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].lineCount").value(2))
                .andExpect(jsonPath("$.data.items[0].totalQuantity").value(800))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"));
        mockMvc.perform(get(BASE).param("status", "DRAFT"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].warehouseName").value("Kho Bắc Ninh"));
        mockMvc.perform(get(BASE)).andExpect(jsonPath("$.data.pagination.totalElements").value(2));
    }

    @Test
    @DisplayName("BR-OPN-05: SP đã có biến động tại kho - bỏ SP của phiếu đầu kỳ đã đảo")
    void productsWithMovementShouldIgnoreReversedOpening() throws Exception {
        long id = createDraft(hanoi, line(cornFeed, 500, null));
        complete(id).andExpect(status().isOk());
        completeInbound(hanoi, pigFeed, 5);

        movement(hanoi).andExpect(jsonPath("$.data.length()").value(2));
        reverse(id, "Nhầm kho").andExpect(status().isOk());
        movement(hanoi)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value(pigFeed.getId()));
        movement(bacNinh).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get(BASE + "/products-with-movement").param("warehouseId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Lịch sử kho: giao dịch đầu kỳ hiện với documentType OPENING_STOCK")
    void stockHistoryShouldShowOpeningType() throws Exception {
        complete(createDraft(hanoi, line(cornFeed, 500, null))).andExpect(status().isOk());

        mockMvc.perform(get("/api/inventory/transactions").param("documentType", "OPENING_STOCK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].documentType").value("OPENING_STOCK"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(500));
    }

    @Test
    @DisplayName("BR-OPN-08: CHECK ở DB - phiếu đầu kỳ không được có NCC")
    void databaseShouldRejectOpeningWithSupplier() {
        Supplier supplier = saveSupplier();
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, warehouse_id, supplier_id,"
                + " document_date, tracks_debt) VALUES ('DTK-X1', 'OPENING_STOCK', ?, ?, CURRENT_DATE, FALSE)",
                hanoi.getId(), supplier.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_opening_stock");
    }

    // Tách riêng: sau một lỗi SQL, transaction của test bị hủy, câu lệnh thứ hai không chạy được
    @Test
    @DisplayName("BR-OPN-08: CHECK ở DB - phiếu đầu kỳ không được theo dõi công nợ")
    void databaseShouldRejectOpeningWithDebt() {
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, warehouse_id,"
                + " document_date, tracks_debt) VALUES ('DTK-X2', 'OPENING_STOCK', ?, CURRENT_DATE, TRUE)", hanoi.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_opening_stock");
    }

    // ===== Helpers =====

    private long createDraft(Warehouse warehouse, String... lines) throws Exception {
        String response = create(body(warehouse, null, lines))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
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

    private ResultActions movement(Warehouse warehouse) throws Exception {
        return mockMvc.perform(get(BASE + "/products-with-movement").param("warehouseId", warehouse.getId().toString()))
                .andExpect(status().isOk());
    }

    /** Phiếu nhập hoàn tất qua API thật — để kho "đã vận hành". */
    private void completeInbound(Warehouse warehouse, Product product, int quantity) throws Exception {
        Supplier supplier = saveSupplier();
        productSupplierRepository.save(new ProductSupplier(supplier, product));
        String response = mockMvc.perform(post("/api/inbound-receipts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\": " + supplier.getId() + ", \"warehouseId\": " + warehouse.getId()
                                + ", \"lines\": [{\"productId\": " + product.getId() + ", \"orderedQuantity\": "
                                + quantity + ", \"unitPrice\": 1000}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = readId(response);
        mockMvc.perform(post("/api/inbound-receipts/{id}/complete", id)).andExpect(status().isOk());
    }

    /** Phiếu bán qua API thật; trả kết quả bước hoàn tất (có thể bị từ chối vì thiếu tồn). */
    private ResultActions completeSale(Warehouse warehouse, Product product, int quantity) throws Exception {
        // SĐT phải đủ 10 số (ck_customer_phone) và không trùng giữa các lần gọi trong một test
        customerSeq++;
        Customer customer = new Customer();
        customer.setName("Khách " + customerSeq);
        customer.setType(CustomerType.DEALER);
        customer.setPhone("09%08d".formatted(customerSeq));
        customerRepository.save(customer);
        String response = mockMvc.perform(post("/api/sales-orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": " + customer.getId() + ", \"warehouseId\": " + warehouse.getId()
                                + ", \"lines\": [{\"productId\": " + product.getId() + ", \"quantity\": " + quantity
                                + ", \"unitPrice\": 1000}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mockMvc.perform(post("/api/sales-orders/{id}/complete", readId(response)));
    }

    private long readId(String response) throws Exception {
        JsonNode data = objectMapper.readTree(response).get("data");
        return data.get("id").asLong();
    }

    private static String body(Warehouse warehouse, LocalDate date, String... lines) {
        String dateField = date == null ? "" : "\"documentDate\": \"" + date + "\", ";
        return "{\"warehouseId\": " + warehouse.getId() + ", " + dateField
                + "\"lines\": [" + String.join(", ", lines) + "]}";
    }

    private static String line(Product product, int quantity, String unitPrice) {
        String priceField = unitPrice == null ? "" : ", \"unitPrice\": " + unitPrice;
        return "{\"productId\": " + product.getId() + ", \"quantity\": " + quantity + priceField + "}";
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }

    // JdbcTemplate đọc thẳng DB → flush trước để thấy thay đổi Hibernate còn giữ trong bộ nhớ
    private Integer inventory(Product product, Warehouse warehouse) {
        entityManager.flush();
        return jdbcTemplate.query("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, product.getId(), warehouse.getId());
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

    private Supplier saveSupplier() {
        Supplier s = new Supplier();
        s.setName("NCC " + System.nanoTime());
        return supplierRepository.save(s);
    }
}
