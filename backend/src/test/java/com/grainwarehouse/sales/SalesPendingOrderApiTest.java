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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đơn khách đặt — phiếu bán Chờ xác nhận (BR-SAL-16..22) qua API. Test rollback sau mỗi ca; các ca
 * tranh chấp (hai người cùng xác nhận / hủy) nằm ở SalesOrderConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesPendingOrderApiTest {

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
        category.setName("Cám test đơn đặt");
        categoryRepository.save(category);
        pigFeed = saveProduct(category, "Cám heo con 25kg");
        chickenFeed = saveProduct(category, "Cám gà thịt 25kg");
        warehouse = saveWarehouse("Kho Hà Nội");
        dealer = saveCustomer("Đại lý Minh Phát", "0901000001");
        farm = saveCustomer("Trại heo Hưng Thịnh", "0901000002");
        setStock(pigFeed, 100);
        setStock(chickenFeed, 50);
    }

    // ===== Ghi đơn =====

    @Test
    @DisplayName("BR-SAL-17: ghi đơn - 201, mã PX, Chờ xác nhận, chưa có kho / giá / dòng phiếu, chưa đổi tồn")
    void shouldCreatePendingOrderWithoutWarehouseOrPrice() throws Exception {
        LocalDate nextWeek = today().plusDays(7);
        createPending(pendingBody(dealer.getId(), "PHONE", nextWeek, request(pigFeed.getId(), 50)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(matchesPattern("PX\\d{6}")))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.orderChannel").value("PHONE"))
                .andExpect(jsonPath("$.data.documentDate").value(today().toString()))
                // Đặt trước: ngày muốn giao được ở tương lai (khác ngày bán)
                .andExpect(jsonPath("$.data.requestedDeliveryDate").value(nextWeek.toString()))
                .andExpect(jsonPath("$.data.warehouseId").doesNotExist())
                .andExpect(jsonPath("$.data.customerName").value("Đại lý Minh Phát"))
                .andExpect(jsonPath("$.data.createdBy").exists())
                .andExpect(jsonPath("$.data.lines.length()").value(0))
                .andExpect(jsonPath("$.data.requestLines.length()").value(1))
                .andExpect(jsonPath("$.data.requestLines[0].productId").value(pigFeed.getId()))
                .andExpect(jsonPath("$.data.requestLines[0].unitName").value("Bao"))
                .andExpect(jsonPath("$.data.requestLines[0].quantity").value(50))
                .andExpect(jsonPath("$.data.totalAmount").value(0));

        assertThat(stock(pigFeed)).isEqualTo(100);
        assertThat(count("SELECT count(*) FROM stock_document_item")).isZero();
    }

    @Test
    @DisplayName("BR-SAL-17: thiếu kênh, không có SP, SL <= 0 - 400 VALIDATION_FAILED")
    void shouldValidatePendingOrder() throws Exception {
        createPending(pendingBody(dealer.getId(), null, null, request(pigFeed.getId(), 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("orderChannel"));
        createPending(pendingBody(dealer.getId(), "PHONE", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines"));
        createPending(pendingBody(dealer.getId(), "PHONE", null, request(pigFeed.getId(), 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[0].quantity"));
    }

    @Test
    @DisplayName("BR-SAL-17: kênh App để dành cho app khách - kho ghi hộ bị từ chối 400")
    void shouldRejectAppChannelFromStaff() throws Exception {
        createPending(pendingBody(dealer.getId(), "APP", null, request(pigFeed.getId(), 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("orderChannel"));
    }

    @Test
    @DisplayName("BR-SAL-17: SP lặp - 400; SP đã ngừng kinh doanh hoặc khách ngừng hoạt động - 409")
    void shouldRejectDuplicateOrInactiveReferences() throws Exception {
        createPending(pendingBody(dealer.getId(), "ZALO", null, request(pigFeed.getId(), 5), request(pigFeed.getId(), 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("lines[1].productId"));

        deactivate("product", chickenFeed.getId());
        createPending(pendingBody(dealer.getId(), "ZALO", null, request(chickenFeed.getId(), 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("ngừng kinh doanh")));

        deactivate("customer", farm.getId());
        createPending(pendingBody(farm.getId(), "ZALO", null, request(pigFeed.getId(), 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("ngừng hoạt động")));
    }

    // ===== Sửa phần khách đặt =====

    @Test
    @DisplayName("BR-SAL-18: sửa đơn chờ xác nhận - đổi SL, bỏ SP, thêm SP, đổi ngày giao và kênh")
    void shouldUpdatePendingRequest() throws Exception {
        long id = pendingId(request(pigFeed.getId(), 50));

        updatePending(id, pendingBody(dealer.getId(), "ZALO", today().plusDays(2), request(chickenFeed.getId(), 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.orderChannel").value("ZALO"))
                .andExpect(jsonPath("$.data.requestedDeliveryDate").value(today().plusDays(2).toString()))
                .andExpect(jsonPath("$.data.requestLines.length()").value(1))
                .andExpect(jsonPath("$.data.requestLines[0].productId").value(chickenFeed.getId()))
                .andExpect(jsonPath("$.data.requestLines[0].quantity").value(30));

        // Giữ SP cũ, đổi SL: cập nhật tại chỗ, không tạo dòng mới
        updatePending(id, pendingBody(dealer.getId(), "ZALO", null, request(chickenFeed.getId(), 35),
                request(pigFeed.getId(), 10)))
                .andExpect(jsonPath("$.data.requestLines.length()").value(2))
                .andExpect(jsonPath("$.data.requestLines[0].quantity").value(35))
                .andExpect(jsonPath("$.data.requestedDeliveryDate").doesNotExist());
        assertThat(count("SELECT count(*) FROM sale_request_item WHERE document_id = " + id)).isEqualTo(2);
    }

    @Test
    @DisplayName("BR-SAL-18: không đổi được khách của đơn khách đặt - 400 field customerId")
    void shouldNotChangeCustomerOfPendingOrder() throws Exception {
        long id = pendingId(request(pigFeed.getId(), 50));

        updatePending(id, pendingBody(farm.getId(), "PHONE", null, request(pigFeed.getId(), 50)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("customerId"));
    }

    // ===== Xác nhận =====

    @Test
    @DisplayName("BR-SAL-19, 20: xác nhận đổi SP và SL - thành Nháp có kho và giá; phần khách đặt giữ nguyên; chưa trừ tồn")
    void shouldConfirmWithSubstitutedProduct() throws Exception {
        long id = pendingId(request(pigFeed.getId(), 50));

        // Hết cám heo, tư vấn khách lấy 40 bao cám gà
        confirm(id, confirmBody(dealer.getId(), null, line(chickenFeed.getId(), 40, "250000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.orderChannel").value("PHONE"))
                .andExpect(jsonPath("$.data.warehouseId").value(warehouse.getId()))
                .andExpect(jsonPath("$.data.confirmedBy").exists())
                .andExpect(jsonPath("$.data.confirmedAt").exists())
                .andExpect(jsonPath("$.data.lines.length()").value(1))
                .andExpect(jsonPath("$.data.lines[0].productId").value(chickenFeed.getId()))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(40))
                .andExpect(jsonPath("$.data.totalAmount").value(10000000))
                .andExpect(jsonPath("$.data.requestLines.length()").value(1))
                .andExpect(jsonPath("$.data.requestLines[0].productId").value(pigFeed.getId()))
                .andExpect(jsonPath("$.data.requestLines[0].quantity").value(50));
        assertThat(stock(chickenFeed)).isEqualTo(50);

        // Hoàn tất như phiếu nháp thường: trừ đúng SP thực giao
        mockMvc.perform(post(BASE + "/{id}/complete", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.requestLines[0].quantity").value(50));
        assertThat(stock(chickenFeed)).isEqualTo(10);
        assertThat(stock(pigFeed)).isEqualTo(100);
    }

    @Test
    @DisplayName("BR-SAL-20: đã xác nhận thì không sửa được phần khách đặt, không xác nhận lại được - 409")
    void shouldLockRequestAfterConfirm() throws Exception {
        long id = pendingId(request(pigFeed.getId(), 50));
        confirm(id, confirmBody(dealer.getId(), null, line(pigFeed.getId(), 50, "300000"))).andExpect(status().isOk());

        updatePending(id, pendingBody(dealer.getId(), "PHONE", null, request(pigFeed.getId(), 60)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Chờ xác nhận")));
        confirm(id, confirmBody(dealer.getId(), null, line(pigFeed.getId(), 50, "300000")))
                .andExpect(status().isConflict());
        assertThat(count("SELECT quantity FROM sale_request_item WHERE document_id = " + id)).isEqualTo(50);
    }

    @Test
    @DisplayName("BR-SAL-19: xác nhận áp dụng kiểm tra phiếu nháp - thiếu kho 400, ngày bán tương lai 400, đổi khách 400")
    void confirmShouldValidateLikeDraft() throws Exception {
        long id = pendingId(request(pigFeed.getId(), 50));

        String noWarehouse = "{\"customerId\": " + dealer.getId() + ", \"lines\": [" + line(pigFeed.getId(), 5, "1000") + "]}";
        confirm(id, noWarehouse)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("warehouseId"));
        confirm(id, confirmBody(dealer.getId(), today().plusDays(1), line(pigFeed.getId(), 5, "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("documentDate"));
        confirm(id, confirmBody(farm.getId(), null, line(pigFeed.getId(), 5, "1000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("customerId"));

        mockMvc.perform(get(BASE + "/{id}", id)).andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("BR-SAL-16: đơn chờ xác nhận không hoàn tất, không sửa như nháp được - 409")
    void pendingOrderShouldNotSkipConfirmation() throws Exception {
        long id = pendingId(request(pigFeed.getId(), 50));

        mockMvc.perform(post(BASE + "/{id}/complete", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Chờ xác nhận")));
        mockMvc.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(dealer.getId(), null, line(pigFeed.getId(), 50, "1000"))))
                .andExpect(status().isConflict());
        assertThat(stock(pigFeed)).isEqualTo(100);
    }

    @Test
    @DisplayName("Phiếu nháp lập từ đơn khách đặt giữ nguyên khách khi sửa - 400; phiếu kho lập trực tiếp vẫn đổi khách được")
    void confirmedDraftShouldKeepCustomer() throws Exception {
        long id = pendingId(request(pigFeed.getId(), 50));
        confirm(id, confirmBody(dealer.getId(), null, line(pigFeed.getId(), 50, "1000"))).andExpect(status().isOk());

        mockMvc.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(farm.getId(), null, line(pigFeed.getId(), 50, "1000"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("customerId"));

        JsonNode direct = data(mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody(dealer.getId(), null, line(pigFeed.getId(), 5, "1000")))));
        assertThat(direct.get("orderChannel").asText()).isEqualTo("DIRECT");
        assertThat(direct.get("requestLines")).isEmpty();
        mockMvc.perform(put(BASE + "/{id}", direct.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(farm.getId(), null, line(pigFeed.getId(), 5, "1000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerId").value(farm.getId()));
    }

    // ===== Hủy =====

    @Test
    @DisplayName("BR-SAL-21: hủy đơn chờ xác nhận bắt buộc lý do - thiếu 400; có lý do thì Đã hủy, phần khách đặt còn giữ")
    void cancelPendingShouldRequireReason() throws Exception {
        long id = pendingId(request(pigFeed.getId(), 50));

        mockMvc.perform(post(BASE + "/{id}/cancel", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("reason"));

        cancel(id, "Khách đổi ý")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.reason").value("Khách đổi ý"))
                .andExpect(jsonPath("$.data.warehouseId").doesNotExist())
                .andExpect(jsonPath("$.data.requestLines[0].quantity").value(50));
        confirm(id, confirmBody(dealer.getId(), null, line(pigFeed.getId(), 50, "1000")))
                .andExpect(status().isConflict());
    }

    // ===== Danh sách, Tổng quan =====

    @Test
    @DisplayName("Lọc danh sách theo trạng thái Chờ xác nhận, kênh và ngày muốn giao; phiếu chưa có kho vẫn hiện")
    void shouldFilterPendingOrders() throws Exception {
        LocalDate tomorrow = today().plusDays(1);
        long tomorrowOrder = data(createPending(pendingBody(dealer.getId(), "ZALO", tomorrow, request(pigFeed.getId(), 5))))
                .get("id").asLong();
        pendingId(request(chickenFeed.getId(), 5));

        mockMvc.perform(get(BASE).param("status", "PENDING").param("customerId", dealer.getId().toString())
                        .param("deliveryDateFrom", tomorrow.toString()).param("deliveryDateTo", tomorrow.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(tomorrowOrder))
                .andExpect(jsonPath("$.data.items[0].requestedDeliveryDate").value(tomorrow.toString()))
                .andExpect(jsonPath("$.data.items[0].warehouseName").doesNotExist());
        mockMvc.perform(get(BASE).param("orderChannel", "PHONE").param("customerId", dealer.getId().toString()))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("BR-SAL-22: Tổng quan đếm đơn chờ xác nhận riêng - không tính vào số phiếu nháp")
    void dashboardShouldCountPendingSeparately() throws Exception {
        JsonNode before = data(mockMvc.perform(get("/api/reports/dashboard"))).get("sales");
        long id = pendingId(request(pigFeed.getId(), 50));
        JsonNode afterCreate = data(mockMvc.perform(get("/api/reports/dashboard"))).get("sales");

        assertThat(afterCreate.get("pendingCount").asLong() - before.get("pendingCount").asLong()).isEqualTo(1);
        assertThat(afterCreate.get("draftCount").asLong()).isEqualTo(before.get("draftCount").asLong());

        confirm(id, confirmBody(dealer.getId(), null, line(pigFeed.getId(), 50, "1000"))).andExpect(status().isOk());
        JsonNode afterConfirm = data(mockMvc.perform(get("/api/reports/dashboard"))).get("sales");
        assertThat(afterConfirm.get("pendingCount").asLong()).isEqualTo(before.get("pendingCount").asLong());
        assertThat(afterConfirm.get("draftCount").asLong() - before.get("draftCount").asLong()).isEqualTo(1);
    }

    // ===== Helpers =====

    private long pendingId(String... requestLines) throws Exception {
        return data(createPending(pendingBody(dealer.getId(), "PHONE", null, requestLines))).get("id").asLong();
    }

    private ResultActions createPending(String body) throws Exception {
        return mockMvc.perform(post(BASE + "/pending").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions updatePending(long id, String body) throws Exception {
        return mockMvc.perform(put(BASE + "/{id}/request", id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions confirm(long id, String body) throws Exception {
        return mockMvc.perform(post(BASE + "/{id}/confirm", id).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions cancel(long id, String reason) throws Exception {
        return mockMvc.perform(post(BASE + "/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", reason))));
    }

    private JsonNode data(ResultActions result) throws Exception {
        String response = result.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data");
    }

    private static String pendingBody(Long customerId, String channel, LocalDate deliveryDate, String... lines) {
        String channelField = channel == null ? "" : "\"orderChannel\": \"" + channel + "\", ";
        String dateField = deliveryDate == null ? "" : "\"requestedDeliveryDate\": \"" + deliveryDate + "\", ";
        return "{\"customerId\": " + customerId + ", " + channelField + dateField
                + "\"lines\": [" + String.join(", ", lines) + "]}";
    }

    private static String request(Long productId, int quantity) {
        return "{\"productId\": " + productId + ", \"quantity\": " + quantity + "}";
    }

    // Mọi phiếu xác nhận xuất từ cùng một kho
    private String confirmBody(Long customerId, LocalDate documentDate, String... lines) {
        String dateField = documentDate == null ? "" : "\"documentDate\": \"" + documentDate + "\", ";
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

    // Ghi thẳng DB rồi clear: Hibernate không còn giữ bản "đang hoạt động" cũ trong bộ nhớ của transaction test
    private void deactivate(String table, Long id) {
        entityManager.flush();
        jdbcTemplate.update("UPDATE " + table + " SET active = FALSE WHERE id = ?", id);
        entityManager.clear();
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
