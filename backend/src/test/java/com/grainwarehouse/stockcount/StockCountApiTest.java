package com.grainwarehouse.stockcount;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
 * Kiểm kê qua API (BR-CNT-01..10). Tồn ban đầu dựng bằng phiếu tồn đầu kỳ qua API thật: cám A 120, cám B 50, cám C
 * chưa có tồn. Ghi đồng thời và rollback giữa chừng (cần commit thật): StockCountConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StockCountApiTest {

    private static final String BASE = "/api/stock-counts";

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

    private Warehouse warehouse;
    private Product cornA;
    private Product cornB;
    private Product cornC;
    private long lossReason;

    @BeforeEach
    void setUp() throws Exception {
        Category category = new Category();
        category.setName("Cám test");
        categoryRepository.save(category);
        cornA = saveProduct(category, "Cám A 25kg");
        cornB = saveProduct(category, "Cám B 25kg");
        cornC = saveProduct(category, "Cám C 25kg");
        warehouse = new Warehouse();
        warehouse.setName("Kho Hà Nội");
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Địa chỉ test");
        warehouseRepository.save(warehouse);

        String opening = mockMvc.perform(post("/api/opening-stocks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\": " + warehouse.getId() + ", \"lines\": [{\"productId\": " + cornA.getId()
                                + ", \"quantity\": 120}, {\"productId\": " + cornB.getId() + ", \"quantity\": 50}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/opening-stocks/{id}/complete", objectMapper.readTree(opening).get("data").get("id").asLong()))
                .andExpect(status().isOk());

        lossReason = data(mockMvc.perform(post("/api/stock-count-reasons").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Hao hụt test\"}")).andExpect(status().isCreated())).get("id").asLong();
    }

    // ===== Lập phiếu =====

    @Test
    @DisplayName("BR-CNT-02, 03: lập phiếu - mã KK, nạp sẵn SP đang có tồn kèm SL sổ sách, chưa đếm, chưa đổi tồn")
    void shouldCreateWithBookQuantities() throws Exception {
        create()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(matchesPattern("KK\\d{6}")))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.bookSnapshotAt").exists())
                .andExpect(jsonPath("$.data.createdBy").exists())
                .andExpect(jsonPath("$.data.lineCount").value(2))
                .andExpect(jsonPath("$.data.countedLineCount").value(0))
                .andExpect(jsonPath("$.data.lines[0].productCode").value(cornA.getCode()))
                .andExpect(jsonPath("$.data.lines[0].bookQuantity").value(120))
                .andExpect(jsonPath("$.data.lines[0].countedQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.lines[1].bookQuantity").value(50))
                .andExpect(jsonPath("$.data.movedSinceSnapshot.length()").value(0));

        assertThat(inventory(cornA)).isEqualTo(120);
    }

    @Test
    @DisplayName("BR-CNT-07: kho đang có phiếu kiểm dở - lập phiếu mới 409 nêu mã phiếu; hủy phiếu dở thì lập được")
    void shouldAllowOnlyOneOpenCountPerWarehouse() throws Exception {
        JsonNode first = data(create().andExpect(status().isCreated()));

        create().andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString(first.get("code").asText())));

        mockMvc.perform(post(BASE + "/{id}/cancel", first.get("id").asLong()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        create().andExpect(status().isCreated());
    }

    @Test
    @DisplayName("BR-CNT-01: kho ngừng hoạt động - 409; ngày tương lai - 400; kho không có - 404")
    void shouldValidateCreate() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseId\": " + warehouse.getId() + ", \"documentDate\": \""
                                + LocalDateToday.plusOne() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("documentDate"));
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"warehouseId\": 999999}"))
                .andExpect(status().isNotFound());
        warehouse.setActive(false);
        create().andExpect(status().isConflict());
    }

    // ===== Đếm, lưu nháp =====

    @Test
    @DisplayName("BR-CNT-04: lưu nháp - SL đếm, thêm SP chưa có tồn (sổ sách 0), tổng thừa / thiếu; SL đếm âm - 400")
    void shouldSaveCounts() throws Exception {
        long id = createId();

        update(id, line(cornA, 115, lossReason, "Bao rách"), line(cornB, 50, null, null), line(cornC, 3, lossReason, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lineCount").value(3))
                .andExpect(jsonPath("$.data.countedLineCount").value(3))
                .andExpect(jsonPath("$.data.differenceLineCount").value(2))
                .andExpect(jsonPath("$.data.surplusQuantity").value(3))
                .andExpect(jsonPath("$.data.shortageQuantity").value(5))
                .andExpect(jsonPath("$.data.lines[0].difference").value(-5))
                .andExpect(jsonPath("$.data.lines[0].reasonName").value("Hao hụt test"))
                .andExpect(jsonPath("$.data.lines[0].reasonNote").value("Bao rách"))
                .andExpect(jsonPath("$.data.lines[2].bookQuantity").value(0))
                .andExpect(jsonPath("$.data.lines[2].difference").value(3));

        update(id, line(cornA, -1, null, null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].countedQuantity"));
        update(id, line(cornA, 1, 999999L, null)).andExpect(status().isNotFound());
        assertThat(inventory(cornA)).isEqualTo(120);
    }

    // ===== Xác nhận =====

    @Test
    @DisplayName("BR-CNT-04, 05: còn dòng chưa đếm / dòng lệch thiếu lý do / lý do ngừng dùng - 409, phiếu vẫn đang kiểm")
    void shouldRequireCountsAndReasons() throws Exception {
        long id = createId();

        update(id, line(cornA, 115, null, null), line(cornB, null, null, null)).andExpect(status().isOk());
        complete(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString(cornB.getCode())));

        update(id, line(cornA, 115, null, null), line(cornB, 50, null, null)).andExpect(status().isOk());
        complete(id).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("lý do")))
                .andExpect(jsonPath("$.error.message").value(containsString(cornA.getCode())));

        update(id, line(cornA, 115, lossReason, null), line(cornB, 50, null, null)).andExpect(status().isOk());
        mockMvc.perform(put("/api/stock-count-reasons/{id}", lossReason).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Hao hụt test\", \"active\": false}")).andExpect(status().isOk());
        complete(id).andExpect(status().isConflict());

        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(inventory(cornA)).isEqualTo(120);
    }

    @Test
    @DisplayName("BR-CNT-06: xác nhận - chỉ ghi phần chênh: thiếu → OUT, thừa → IN, khớp → không ghi; ghi người xác nhận")
    void shouldPostDifferencesOnly() throws Exception {
        long id = createId();
        update(id, line(cornA, 115, lossReason, null), line(cornB, 50, null, null), line(cornC, 3, lossReason, null))
                .andExpect(status().isOk());

        complete(id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedBy").exists())
                .andExpect(jsonPath("$.data.movedSinceSnapshot.length()").value(0));

        assertThat(inventory(cornA)).isEqualTo(115);
        assertThat(inventory(cornB)).isEqualTo(50);
        assertThat(inventory(cornC)).isEqualTo(3);
        List<Map<String, Object>> transactions = transactionsOf(id);
        assertThat(transactions).hasSize(2);
        assertThat(transactions).anySatisfy(t -> {
            assertThat(t.get("product_id")).isEqualTo(cornA.getId());
            assertThat(t.get("type")).isEqualTo("OUT");
            assertThat(t.get("quantity_before")).isEqualTo(120);
            assertThat(t.get("quantity_after")).isEqualTo(115);
        });
        assertThat(transactions).anySatisfy(t -> {
            assertThat(t.get("product_id")).isEqualTo(cornC.getId());
            assertThat(t.get("type")).isEqualTo("IN");
        });
    }

    @Test
    @DisplayName("BR-CNT-03 (cách c): bán 10 bao SAU lúc chụp sổ sách - cảnh báo SP đó; xác nhận ghi chênh −5 → tồn 105")
    void saleDuringCountShouldKeepItsEffect() throws Exception {
        long id = createId();
        sell(cornA, 10).andExpect(status().isOk());   // tồn 110, sổ sách trên phiếu vẫn 120

        update(id, line(cornA, 115, lossReason, null), line(cornB, 50, null, null))
                .andExpect(jsonPath("$.data.movedSinceSnapshot.length()").value(1))
                .andExpect(jsonPath("$.data.movedSinceSnapshot[0]").value(cornA.getId()));
        complete(id).andExpect(status().isOk());

        // Đặt thẳng tồn = 115 sẽ làm 10 bao vừa bán "sống lại"; ghi chênh thì 110 − 5 = 105
        assertThat(inventory(cornA)).isEqualTo(105);
    }

    @Test
    @DisplayName("BR-CNT-03: lấy lại số sổ sách - SL sổ sách theo tồn hiện tại, SL đếm giữ nguyên, hết cảnh báo")
    void refreshBookShouldResnapshot() throws Exception {
        long id = createId();
        update(id, line(cornA, 115, lossReason, null), line(cornB, 50, null, null)).andExpect(status().isOk());
        sell(cornA, 10).andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/{id}/refresh-book", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[0].bookQuantity").value(110))
                .andExpect(jsonPath("$.data.lines[0].countedQuantity").value(115))
                .andExpect(jsonPath("$.data.lines[0].difference").value(5))
                .andExpect(jsonPath("$.data.movedSinceSnapshot.length()").value(0));
    }

    @Test
    @DisplayName("BR-CNT-08: đảo - giao dịch ngược lại, tồn về như trước; đảo lần hai - 409; sửa phiếu đã xác nhận - 409")
    void shouldReverse() throws Exception {
        long id = createId();
        update(id, line(cornA, 115, lossReason, null), line(cornB, 52, lossReason, null)).andExpect(status().isOk());
        complete(id).andExpect(status().isOk());

        update(id, line(cornA, 100, lossReason, null)).andExpect(status().isConflict());
        mockMvc.perform(post(BASE + "/{id}/reverse", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Đếm nhầm kệ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVERSED"));
        mockMvc.perform(post(BASE + "/{id}/reverse", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"Lần nữa\"}"))
                .andExpect(status().isConflict());

        assertThat(inventory(cornA)).isEqualTo(120);
        assertThat(inventory(cornB)).isEqualTo(50);
        assertThat(transactionsOf(id)).hasSize(4);
    }

    // ===== Đọc, danh mục =====

    @Test
    @DisplayName("BR-CNT-09: danh sách - tổng hợp số dòng, đã đếm, lệch, thừa, thiếu; lịch sử kho có loại STOCK_COUNT")
    void listAndHistory() throws Exception {
        long id = createId();
        update(id, line(cornA, 115, lossReason, null), line(cornB, null, null, null), line(cornC, 3, lossReason, null))
                .andExpect(status().isOk());

        mockMvc.perform(get(BASE).param("warehouseId", warehouse.getId().toString()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].lineCount").value(3))
                .andExpect(jsonPath("$.data.items[0].countedLineCount").value(2))
                .andExpect(jsonPath("$.data.items[0].differenceLineCount").value(2))
                .andExpect(jsonPath("$.data.items[0].surplusQuantity").value(3))
                .andExpect(jsonPath("$.data.items[0].shortageQuantity").value(5));

        update(id, line(cornA, 115, lossReason, null), line(cornC, 3, lossReason, null)).andExpect(status().isOk());
        complete(id).andExpect(status().isOk());
        mockMvc.perform(get("/api/inventory/transactions").param("documentType", "STOCK_COUNT"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].documentType").value("STOCK_COUNT"));
    }

    @Test
    @DisplayName("BR-CNT-05: danh mục lý do - có sẵn lý do mặc định; trùng tên (không phân biệt hoa thường) - 409")
    void reasonsShouldBeManaged() throws Exception {
        mockMvc.perform(get("/api/stock-count-reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name == 'Mất mát')]").exists());
        mockMvc.perform(post("/api/stock-count-reasons").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"HAO HỤT TEST\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("CHECK ở DB - quantity của dòng kiểm kê phải bằng |đếm − sổ sách|")
    void databaseShouldKeepQuantityConsistent() throws Exception {
        long id = createId();
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE stock_document_item SET counted_quantity = 100, quantity = 1"
                + " WHERE document_id = ? AND product_id = ?", id, cornA.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_item_count");
    }

    @Test
    @DisplayName("BR-CNT-07: UNIQUE ở DB - hai phiếu kiểm đang dở cho một kho bị chặn")
    void databaseShouldRejectTwoOpenCounts() throws Exception {
        createId();
        entityManager.flush();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, warehouse_id, document_date,"
                + " tracks_debt, book_snapshot_at) VALUES ('KK-X1', 'STOCK_COUNT', ?, CURRENT_DATE, FALSE, now())",
                warehouse.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_stock_document_open_count");
    }

    // ===== Helpers =====

    private ResultActions create() throws Exception {
        return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseId\": " + warehouse.getId() + "}"));
    }

    private long createId() throws Exception {
        return data(create().andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions update(long id, String... lines) throws Exception {
        return mockMvc.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\": [" + String.join(", ", lines) + "]}"));
    }

    private ResultActions complete(long id) throws Exception {
        return mockMvc.perform(post(BASE + "/{id}/complete", id));
    }

    private static String line(Product product, Integer counted, Long reasonId, String note) {
        return "{\"productId\": " + product.getId()
                + (counted == null ? "" : ", \"countedQuantity\": " + counted)
                + (reasonId == null ? "" : ", \"reasonId\": " + reasonId)
                + (note == null ? "" : ", \"reasonNote\": \"" + note + "\"") + "}";
    }

    /** Bán qua API thật; trả kết quả bước hoàn tất. */
    private ResultActions sell(Product product, int quantity) throws Exception {
        Customer customer = new Customer();
        customer.setName("Khách " + quantity + product.getId());
        customer.setType(CustomerType.DEALER);
        customer.setPhone("09%08d".formatted(product.getId() * 100 + quantity));
        customerRepository.save(customer);
        String order = mockMvc.perform(post("/api/sales-orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\": " + customer.getId() + ", \"warehouseId\": " + warehouse.getId()
                                + ", \"lines\": [{\"productId\": " + product.getId() + ", \"quantity\": " + quantity
                                + ", \"unitPrice\": 1000}]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mockMvc.perform(post("/api/sales-orders/{id}/complete", objectMapper.readTree(order).get("data").get("id").asLong()));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    private Integer inventory(Product product) {
        entityManager.flush();
        return jdbcTemplate.query("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, product.getId(), warehouse.getId());
    }

    private List<Map<String, Object>> transactionsOf(long documentId) {
        entityManager.flush();
        return jdbcTemplate.queryForList("SELECT t.* FROM stock_transaction t"
                + " JOIN stock_document_item i ON i.id = t.document_item_id WHERE i.document_id = ? ORDER BY t.id", documentId);
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

    /** Ngày mai theo giờ Việt Nam. */
    private static final class LocalDateToday {
        static String plusOne() {
            return java.time.LocalDate.now(CommonSpecifications.BUSINESS_ZONE).plusDays(1).toString();
        }
    }
}
