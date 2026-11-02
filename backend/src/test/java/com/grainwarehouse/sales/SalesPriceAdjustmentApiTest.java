package com.grainwarehouse.sales;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Điều chỉnh giá phiếu bán đã giao, chưa thu tiền (BR-SAL-23..26) qua API. Test rollback sau mỗi ca; ca tranh
 * chấp với thu tiền nằm ở SalesOrderConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesPriceAdjustmentApiTest {

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
    private Customer customer;
    private Product pigFeed;
    private Product chickenFeed;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("Cám test điều chỉnh giá");
        categoryRepository.save(category);
        pigFeed = saveProduct(category, "Cám heo con 25kg");
        chickenFeed = saveProduct(category, "Cám gà thịt 25kg");
        warehouse = new Warehouse();
        warehouse.setName("Kho điều chỉnh giá");
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Địa chỉ test");
        warehouseRepository.save(warehouse);
        customer = new Customer();
        customer.setName("Đại lý Minh Phát");
        customer.setType(CustomerType.DEALER);
        customer.setPhone("0901000009");
        customerRepository.save(customer);
        setStock(pigFeed, 100);
        setStock(chickenFeed, 100);
    }

    @Test
    @DisplayName("BR-SAL-23, 24: phiếu đã giao chưa thu - đổi giá một dòng, tính lại tổng và còn nợ, tồn không đổi")
    void shouldAdjustPriceOfDeliveredUnpaidOrder() throws Exception {
        // 10 bao × 300.000 + 20 bao × 250.000 = 8.000.000
        long id = completedOrder(line(pigFeed, 10, "300000"), line(chickenFeed, 20, "250000"));
        int transactionsBefore = count("SELECT count(*) FROM stock_transaction");

        adjust(id, "Bớt giá cho khách quen", adjustLine(pigFeed, "295000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(295000))
                .andExpect(jsonPath("$.data.lines[0].lineAmount").value(2950000))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(10))
                .andExpect(jsonPath("$.data.totalAmount").value(7950000))
                .andExpect(jsonPath("$.data.outstandingAmount").value(7950000))
                .andExpect(jsonPath("$.data.priceAdjustments.length()").value(1))
                .andExpect(jsonPath("$.data.priceAdjustments[0].productId").value(pigFeed.getId()))
                .andExpect(jsonPath("$.data.priceAdjustments[0].oldUnitPrice").value(300000))
                .andExpect(jsonPath("$.data.priceAdjustments[0].newUnitPrice").value(295000))
                .andExpect(jsonPath("$.data.priceAdjustments[0].oldLineAmount").value(3000000))
                .andExpect(jsonPath("$.data.priceAdjustments[0].newLineAmount").value(2950000))
                .andExpect(jsonPath("$.data.priceAdjustments[0].reason").value("Bớt giá cho khách quen"))
                .andExpect(jsonPath("$.data.priceAdjustments[0].adjustedBy").exists());

        // Số lượng không đổi → tồn và lịch sử kho giữ nguyên
        assertThat(stock(pigFeed)).isEqualTo(90);
        assertThat(count("SELECT count(*) FROM stock_transaction")).isEqualTo(transactionsBefore);
        mockMvc.perform(get("/api/customer-debts").param("customerId", customer.getId().toString()))
                .andExpect(jsonPath("$.data[0].outstandingAmount").value(7950000));
    }

    @Test
    @DisplayName("BR-SAL-24: dòng gửi lên trùng giá hiện tại thì bỏ qua; mọi dòng đều trùng - 400 field lines")
    void shouldSkipUnchangedLinesAndRejectNoChange() throws Exception {
        long id = completedOrder(line(pigFeed, 10, "300000"), line(chickenFeed, 20, "250000"));

        adjust(id, "Bớt giá", adjustLine(pigFeed, "300000.00"), adjustLine(chickenFeed, "240000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priceAdjustments.length()").value(1))
                .andExpect(jsonPath("$.data.priceAdjustments[0].productId").value(chickenFeed.getId()));

        adjust(id, "Bớt giá", adjustLine(pigFeed, "300000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines"));
    }

    @Test
    @DisplayName("BR-SAL-24: thiếu lý do - 400; SP không có trên phiếu - 400; giá âm - 400")
    void shouldValidateAdjustment() throws Exception {
        long id = completedOrder(line(pigFeed, 10, "300000"));

        adjust(id, "  ", adjustLine(pigFeed, "290000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("reason"));
        adjust(id, "Bớt giá", adjustLine(chickenFeed, "290000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].productId"));
        adjust(id, "Bớt giá", adjustLine(pigFeed, "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].unitPrice"));
        assertThat(count("SELECT count(*) FROM sale_price_adjustment WHERE document_id = " + id)).isZero();
    }

    @Test
    @DisplayName("BR-SAL-25: khách đã trả từ 1 đồng - 409; hoàn hết khoản thu (đã thu về 0) thì điều chỉnh được lại")
    void shouldRejectWhenAnythingPaid() throws Exception {
        long id = completedOrder(line(pigFeed, 10, "300000"));
        JsonNode paid = data(mockMvc.perform(post(BASE + "/{id}/payments", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cashAmount\": 1000}")));

        adjust(id, "Bớt giá", adjustLine(pigFeed, "290000"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("chưa trả đồng nào")));

        long paymentId = paid.get("payments").get(0).get("id").asLong();
        mockMvc.perform(post(BASE + "/{id}/payments/{paymentId}/reverse", id, paymentId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Thu nhầm\"}"))
                .andExpect(status().isOk());
        adjust(id, "Bớt giá", adjustLine(pigFeed, "290000")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("BR-SAL-25: phiếu Nháp (sửa phiếu như cũ) và phiếu Đã đảo không điều chỉnh giá được - 409")
    void shouldOnlyAdjustCompletedOrders() throws Exception {
        long draft = data(createDraft(line(pigFeed, 10, "300000"))).get("id").asLong();
        adjust(draft, "Bớt giá", adjustLine(pigFeed, "290000")).andExpect(status().isConflict());

        long reversed = completedOrder(line(pigFeed, 10, "300000"));
        mockMvc.perform(post(BASE + "/{id}/reverse", reversed).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"Giao sai\"}")).andExpect(status().isOk());
        adjust(reversed, "Bớt giá", adjustLine(pigFeed, "290000"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Đã đảo")));
    }

    @Test
    @DisplayName("BR-SAL-26: điều chỉnh nhiều lần - lịch sử đủ theo thứ tự, giá cũ lần sau = giá mới lần trước")
    void shouldKeepFullAdjustmentHistory() throws Exception {
        long id = completedOrder(line(pigFeed, 10, "300000"));

        adjust(id, "Bớt lần 1", adjustLine(pigFeed, "295000")).andExpect(status().isOk());
        adjust(id, "Bớt thêm", adjustLine(pigFeed, "290000"))
                .andExpect(jsonPath("$.data.totalAmount").value(2900000))
                .andExpect(jsonPath("$.data.priceAdjustments.length()").value(2))
                .andExpect(jsonPath("$.data.priceAdjustments[0].reason").value("Bớt lần 1"))
                .andExpect(jsonPath("$.data.priceAdjustments[1].oldUnitPrice").value(295000))
                .andExpect(jsonPath("$.data.priceAdjustments[1].newUnitPrice").value(290000));

        // Đọc lại chi tiết cũng thấy đủ lịch sử
        mockMvc.perform(get(BASE + "/{id}", id)).andExpect(jsonPath("$.data.priceAdjustments.length()").value(2));
    }

    @Test
    @DisplayName("BR-SAL-24: doanh số hôm nay đổi theo giá mới (phiếu bán hôm nay)")
    void revenueShouldFollowAdjustedPrice() throws Exception {
        long id = completedOrder(line(pigFeed, 10, "300000"));
        JsonNode before = data(mockMvc.perform(get("/api/reports/dashboard"))).get("sales").get("today");

        adjust(id, "Bớt giá", adjustLine(pigFeed, "280000")).andExpect(status().isOk());

        JsonNode after = data(mockMvc.perform(get("/api/reports/dashboard"))).get("sales").get("today");
        assertThat(after.get("revenue").decimalValue().subtract(before.get("revenue").decimalValue()))
                .isEqualByComparingTo("-200000");
        assertThat(after.get("orderCount").asLong()).isEqualTo(before.get("orderCount").asLong());
    }

    // ===== Helpers =====

    private long completedOrder(String... lines) throws Exception {
        long id = data(createDraft(lines)).get("id").asLong();
        mockMvc.perform(post(BASE + "/{id}/complete", id)).andExpect(status().isOk());
        return id;
    }

    private ResultActions createDraft(String... lines) throws Exception {
        String body = "{\"customerId\": " + customer.getId() + ", \"warehouseId\": " + warehouse.getId()
                + ", \"lines\": [" + String.join(", ", lines) + "]}";
        return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions adjust(long id, String reason, String... lines) throws Exception {
        String body = "{\"reason\": " + objectMapper.writeValueAsString(reason)
                + ", \"lines\": [" + String.join(", ", lines) + "]}";
        return mockMvc.perform(post(BASE + "/{id}/price-adjustments", id)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String line(Product product, int quantity, String unitPrice) {
        return "{\"productId\": " + product.getId() + ", \"quantity\": " + quantity + ", \"unitPrice\": " + unitPrice + "}";
    }

    private static String adjustLine(Product product, String unitPrice) {
        return "{\"productId\": " + product.getId() + ", \"unitPrice\": " + unitPrice + "}";
    }

    private JsonNode data(ResultActions result) throws Exception {
        String response = result.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
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

    private int count(String sql) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private Product saveProduct(Category category, String name) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setUnit(unitRepository.findByNameIgnoreCase("Bao").orElseThrow());
        product.setNetWeightKg(new BigDecimal("25"));
        return productRepository.save(product);
    }
}
