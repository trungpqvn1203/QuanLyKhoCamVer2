package com.grainwarehouse.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tồn kho theo sản phẩm (BR-STK-01..06). Dữ liệu tồn ghi thẳng bằng SQL để mỗi ca test kiểm soát được
 * con số chính xác (luồng sinh ra tồn đã được InboundReceipt*Test kiểm chứng riêng).
 *
 * <pre>
 * SP (mức tối thiểu)   Kho HN   Kho HP   Kho cũ (ngừng)   Tổng   Đang về (Nháp)        Tình trạng
 * Cám heo   (50)         30       20        0 (dòng 0)       50    100 tại HN            LOW (= mức)
 * Cám gà    (—)          10        —        —                10     25 tại HP (chưa có tồn) IN_STOCK
 * Cám vịt   (10)          —        —       15                15      —                    IN_STOCK (kho ngừng vẫn tính)
 * Cám cá    (5)           —        —        —                 0      —                    OUT
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductStockApiTest {

    private static final String BASE = "/api/inventory/products";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long pig;
    private long chicken;
    private long duck;
    private long fish;
    private long hanoi;
    private long haiphong;
    private long oldWarehouse;
    private long supplier;

    @BeforeEach
    void setUp() {
        long category = id("INSERT INTO category (name) VALUES ('Cám test') RETURNING id");
        pig = product("Cám heo con 25kg", category, 50);
        chicken = product("Cám gà thịt 25kg", category, null);
        duck = product("Cám vịt 25kg", category, 10);
        fish = product("Cám cá 25kg", category, 5);
        hanoi = warehouse("Kho Hà Nội", true);
        haiphong = warehouse("Kho Hải Phòng", true);
        oldWarehouse = warehouse("Kho cũ", false);
        supplier = id("INSERT INTO supplier (name) VALUES ('NCC test') RETURNING id");

        stock(pig, hanoi, 30);
        stock(pig, haiphong, 20);
        stock(pig, oldWarehouse, 0); // đã từng nhập rồi đảo hết → dòng tồn 0
        stock(chicken, hanoi, 10);
        stock(duck, oldWarehouse, 15);

        // Chỉ phiếu Nháp mới là "đang về"; phiếu Hoàn tất/Đã hủy không tính
        receipt("PN-T1", hanoi, "DRAFT", pig, 100);
        receipt("PN-T2", haiphong, "DRAFT", chicken, 25);
        receipt("PN-T3", haiphong, "COMPLETED", pig, 40);
        receipt("PN-T4", hanoi, "CANCELLED", pig, 70);
    }

    @Test
    @DisplayName("BR-STK-01, 02, 06: tổng tồn mọi kho (kể cả kho ngừng), quy đổi tấn, hàng đang về chỉ từ phiếu Nháp")
    void shouldListProductsWithTotals() throws Exception {
        mockMvc.perform(get(BASE).param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.totalElements").value(4))
                // Cám heo: 30 + 20 + 0 = 50 bao × 25 kg = 1,250 tấn
                .andExpect(jsonPath("$.data.items[0].code").exists())
                .andExpect(jsonPath("$.data.items[0].stockQuantity").value(50))
                .andExpect(jsonPath("$.data.items[0].stockTons").value(1.25))
                .andExpect(jsonPath("$.data.items[0].incomingQuantity").value(100))
                .andExpect(jsonPath("$.data.items[0].minStockQuantity").value(50))
                .andExpect(jsonPath("$.data.items[0].stockStatus").value("LOW"))
                .andExpect(jsonPath("$.data.items[0].categoryName").value("Cám test"))
                // Không lọc theo kho → không có các trường warehouseXxx
                .andExpect(jsonPath("$.data.items[0].warehouseStockQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.data.items[1].incomingQuantity").value(25))
                .andExpect(jsonPath("$.data.items[1].minStockQuantity").doesNotExist())
                .andExpect(jsonPath("$.data.items[2].stockQuantity").value(15))
                .andExpect(jsonPath("$.data.items[2].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.data.items[3].stockQuantity").value(0))
                .andExpect(jsonPath("$.data.items[3].incomingQuantity").value(0))
                .andExpect(jsonPath("$.data.items[3].stockStatus").value("OUT"));
    }

    @Test
    @DisplayName("BR-STK-04: lọc theo tình trạng tồn trong SQL — tổng số và phân trang đúng, khớp stockStatus trả về")
    void shouldFilterByStockStatus() throws Exception {
        mockMvc.perform(get(BASE).param("stockStatus", "LOW"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(pig))
                .andExpect(jsonPath("$.data.items[0].stockStatus").value("LOW"));
        mockMvc.perform(get(BASE).param("stockStatus", "OUT"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(fish));
        mockMvc.perform(get(BASE).param("stockStatus", "IN_STOCK"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[*].id").value(containsInAnyOrder((int) chicken, (int) duck)));
    }

    @Test
    @DisplayName("BR-STK-04: tồn vượt mức tối thiểu 1 đơn vị → hết Sắp hết (cả bộ lọc SQL lẫn stockStatus)")
    void shouldLeaveLowWhenAboveMinimum() throws Exception {
        jdbcTemplate.update("UPDATE inventory SET quantity = 21 WHERE product_id = ? AND warehouse_id = ?", pig, haiphong);

        mockMvc.perform(get(BASE).param("stockStatus", "LOW"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(0));
        mockMvc.perform(get(BASE).param("stockStatus", "IN_STOCK").param("keyword", "heo"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].stockQuantity").value(51))
                .andExpect(jsonPath("$.data.items[0].stockStatus").value("IN_STOCK"));
    }

    @Test
    @DisplayName("BR-STK-05: lọc theo kho - thêm số tồn/đang về tại kho đó, không bớt SP, tình trạng vẫn theo tổng")
    void shouldAddWarehouseFigures() throws Exception {
        mockMvc.perform(get(BASE).param("warehouseId", String.valueOf(hanoi)).param("sort", "id,asc"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(4))
                .andExpect(jsonPath("$.data.items[0].warehouseId").value(hanoi))
                .andExpect(jsonPath("$.data.items[0].warehouseStockQuantity").value(30))
                .andExpect(jsonPath("$.data.items[0].warehouseStockTons").value(0.75))
                .andExpect(jsonPath("$.data.items[0].warehouseIncomingQuantity").value(100))
                .andExpect(jsonPath("$.data.items[0].stockQuantity").value(50))
                .andExpect(jsonPath("$.data.items[0].stockStatus").value("LOW"))
                // Cám gà đang về tại Hải Phòng, không phải Hà Nội
                .andExpect(jsonPath("$.data.items[1].warehouseIncomingQuantity").value(0))
                .andExpect(jsonPath("$.data.items[2].warehouseStockQuantity").value(0))
                .andExpect(jsonPath("$.data.items[2].stockQuantity").value(15));
    }

    @Test
    @DisplayName("Lọc kết hợp từ khóa/danh mục/trạng thái kinh doanh của sản phẩm - giống /api/products")
    void shouldReuseProductFilters() throws Exception {
        jdbcTemplate.update("UPDATE product SET active = false WHERE id = ?", duck);

        mockMvc.perform(get(BASE).param("active", "true").param("keyword", "cam"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(3));
        mockMvc.perform(get(BASE).param("keyword", "VIT"))
                .andExpect(jsonPath("$.data.items[0].id").value(duck))
                .andExpect(jsonPath("$.data.items[0].active").value(false));
    }

    @Test
    @DisplayName("Chi tiết: từng kho có tồn hoặc đang về, sắp theo mã kho; dòng tồn 0 không hiện")
    void shouldShowStockPerWarehouse() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", pig))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(pig))
                .andExpect(jsonPath("$.data.unit").value("BAG"))
                .andExpect(jsonPath("$.data.stockQuantity").value(50))
                .andExpect(jsonPath("$.data.stockTons").value(1.25))
                .andExpect(jsonPath("$.data.incomingQuantity").value(100))
                .andExpect(jsonPath("$.data.stockStatus").value("LOW"))
                .andExpect(jsonPath("$.data.warehouses.length()").value(2))
                .andExpect(jsonPath("$.data.warehouses[0].warehouseId").value(hanoi))
                .andExpect(jsonPath("$.data.warehouses[0].quantity").value(30))
                .andExpect(jsonPath("$.data.warehouses[0].incomingQuantity").value(100))
                .andExpect(jsonPath("$.data.warehouses[0].updatedAt").exists())
                .andExpect(jsonPath("$.data.warehouses[1].warehouseId").value(haiphong))
                .andExpect(jsonPath("$.data.warehouses[1].quantity").value(20))
                .andExpect(jsonPath("$.data.warehouses[1].incomingQuantity").value(0));
    }

    @Test
    @DisplayName("BR-STK-01: kho đã ngừng vẫn hiện (đánh dấu) và tính vào tổng")
    void shouldIncludeInactiveWarehouse() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", duck))
                .andExpect(jsonPath("$.data.stockQuantity").value(15))
                .andExpect(jsonPath("$.data.warehouses.length()").value(1))
                .andExpect(jsonPath("$.data.warehouses[0].warehouseId").value(oldWarehouse))
                .andExpect(jsonPath("$.data.warehouses[0].warehouseActive").value(false));
    }

    @Test
    @DisplayName("BR-STK-06: kho chỉ có hàng đang về (chưa có dòng tồn) vẫn hiện, tồn 0, không có updatedAt")
    void shouldShowWarehouseWithOnlyIncoming() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", chicken))
                .andExpect(jsonPath("$.data.warehouses.length()").value(2))
                .andExpect(jsonPath("$.data.warehouses[1].warehouseId").value(haiphong))
                .andExpect(jsonPath("$.data.warehouses[1].quantity").value(0))
                .andExpect(jsonPath("$.data.warehouses[1].incomingQuantity").value(25))
                .andExpect(jsonPath("$.data.warehouses[1].updatedAt").doesNotExist());
    }

    @Test
    @DisplayName("SP chưa từng nhập - danh sách kho rỗng, Hết hàng")
    void shouldShowEmptyStock() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", fish))
                .andExpect(jsonPath("$.data.stockQuantity").value(0))
                .andExpect(jsonPath("$.data.stockStatus").value("OUT"))
                .andExpect(jsonPath("$.data.warehouses.length()").value(0));
    }

    @Test
    @DisplayName("SP không tồn tại - 404; stockStatus sai - 400")
    void shouldValidate() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get(BASE).param("stockStatus", "ABC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("stockStatus"));
    }

    @Test
    @DisplayName("Trang không có SP nào - danh sách rỗng, không lỗi")
    void shouldHandleEmptyPage() throws Exception {
        mockMvc.perform(get(BASE).param("keyword", "khongcosanphamnay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(0));
    }

    private long product(String name, long category, Integer minStock) {
        return id("INSERT INTO product (name, category_id, unit, net_weight_kg, min_stock_quantity)"
                + " VALUES (?, ?, 'BAG', 25, ?) RETURNING id", name, category, minStock);
    }

    private long warehouse(String name, boolean active) {
        return id("INSERT INTO warehouse (name, type, address, active) VALUES (?, 'CENTRAL', 'Địa chỉ', ?) RETURNING id",
                name, active);
    }

    private void stock(long productId, long warehouseId, int quantity) {
        jdbcTemplate.update("INSERT INTO inventory (product_id, warehouse_id, quantity) VALUES (?, ?, ?)",
                productId, warehouseId, quantity);
    }

    private void receipt(String code, long warehouseId, String status, long productId, int ordered) {
        long documentId = id("INSERT INTO stock_document (code, type, status, warehouse_id, supplier_id, document_date,"
                        + " completed_at, cancelled_at) VALUES (?, 'INBOUND', ?, ?, ?, current_date,"
                        + " CASE WHEN ? = 'COMPLETED' THEN now() END, CASE WHEN ? = 'CANCELLED' THEN now() END)"
                        + " RETURNING id",
                code, status, warehouseId, supplier, status, status);
        jdbcTemplate.update("INSERT INTO stock_document_item (document_id, product_id, ordered_quantity, quantity,"
                + " unit_price, line_amount) VALUES (?, ?, ?, ?, 1000, ?)", documentId, productId, ordered, ordered, ordered * 1000);
    }

    private long id(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
