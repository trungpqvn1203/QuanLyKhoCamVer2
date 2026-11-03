package com.grainwarehouse.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.employee.repository.EmployeeRepository;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

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
 * Xe và chuyến giao hàng (BR-VEH-01..04, BR-DLV-01..10) qua API. Test rollback sau mỗi ca; tranh chấp (hai người
 * cùng xuất, cùng xếp một phiếu) và rollback thật khi trừ tồn giữa chừng ở DeliveryTripConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeliveryTripApiTest {

    private static final String TRIPS = "/api/delivery-trips";
    private static final String VEHICLES = "/api/vehicles";

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
    private EmployeeRepository employeeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Warehouse warehouseA;
    private Warehouse warehouseB;
    private Customer farm;
    private Customer dealer;
    private Product pigFeed;
    private Product chickenFeed;
    private Employee driver;
    private long truckId;

    @BeforeEach
    void setUp() throws Exception {
        Category category = new Category();
        category.setName("Cám test chuyến giao");
        categoryRepository.save(category);
        pigFeed = saveProduct(category, "Cám heo chuyến 25kg");
        chickenFeed = saveProduct(category, "Cám gà chuyến 25kg");
        warehouseA = saveWarehouse("Kho chuyến A");
        warehouseB = saveWarehouse("Kho chuyến B");
        farm = saveCustomer("Trại heo Đồng Tâm", "0901000101", "Xóm 3, Vũ Thư");
        dealer = saveCustomer("Đại lý Minh Phát", "0901000102", "12 Lý Bôn");
        driver = saveEmployee("Lái xe Hùng");
        setStock(pigFeed, warehouseA, 500);
        setStock(chickenFeed, warehouseA, 500);
        setStock(pigFeed, warehouseB, 500);
        // Xe 5 tấn = 200 bao 25 kg
        truckId = data(createVehicle("29h-123.45", "Xe tải Hino", "5")).get("id").asLong();
    }

    // ===== Xe =====

    @Test
    @DisplayName("BR-VEH-01: biển số lưu viết hoa; cùng biển số gõ khác cách (khoảng trắng, chấm, gạch) bị từ chối 409")
    void shouldNormalizeAndRejectDuplicatePlates() throws Exception {
        mockMvc.perform(get(VEHICLES + "/{id}", truckId))
                .andExpect(jsonPath("$.data.plateNumber").value("29H-123.45"))
                .andExpect(jsonPath("$.data.capacityTons").value(5))
                .andExpect(jsonPath("$.data.active").value(true));

        createVehicle("29H 12345", null, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.error.message").value(containsString("29H-123.45")));
    }

    @Test
    @DisplayName("BR-VEH-02: tải trọng để trống được (không cảnh báo); <= 0 hoặc quá 3 chữ số lẻ thì 400")
    void shouldValidateCapacity() throws Exception {
        createVehicle("Ba gác 01", null, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.capacityTons").doesNotExist());
        createVehicle("29C-111.11", null, "0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("capacityTons"));
        createVehicle("29C-111.12", null, "1.2345").andExpect(status().isBadRequest());
        createVehicle("  ", null, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("plateNumber"));
    }

    @Test
    @DisplayName("BR-VEH-04: ngừng dùng xe - không chọn cho chuyến mới; xe trùng đang ngừng thì gợi ý mở lại; mở lại bằng PUT active")
    void shouldDeactivateAndReactivateVehicle() throws Exception {
        mockMvc.perform(delete(VEHICLES + "/{id}", truckId)).andExpect(status().isNoContent());

        createTrip(truckId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("ngừng dùng")));
        createVehicle("29H12345", null, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("mở lại")));

        mockMvc.perform(put(VEHICLES + "/{id}", truckId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNumber\": \"29H-123.45\", \"capacityTons\": 5, \"active\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
        createTrip(truckId).andExpect(status().isCreated());
    }

    // ===== Lập, sửa chuyến =====

    @Test
    @DisplayName("BR-DLV-01, 04, 05: lập chuyến - mã CG, điểm giao đúng thứ tự gửi lên, địa chỉ khách, tổng tấn, vượt tải")
    void shouldCreateTripInRequestedOrder() throws Exception {
        long first = draft(farm, warehouseA, line(pigFeed, 120));      // 3 tấn
        long second = draft(dealer, warehouseB, line(pigFeed, 100));   // 2,5 tấn — bốc ở kho khác (BR-DLV-03)

        createTrip(truckId, second, first)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(matchesPattern("CG\\d{6}")))
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andExpect(jsonPath("$.data.vehiclePlate").value("29H-123.45"))
                .andExpect(jsonPath("$.data.driverName").value("Lái xe Hùng"))
                .andExpect(jsonPath("$.data.stopCount").value(2))
                .andExpect(jsonPath("$.data.stops[0].stopOrder").value(1))
                .andExpect(jsonPath("$.data.stops[0].documentId").value(second))
                .andExpect(jsonPath("$.data.stops[0].customerAddress").value("12 Lý Bôn"))
                .andExpect(jsonPath("$.data.stops[0].warehouseName").value("Kho chuyến B"))
                .andExpect(jsonPath("$.data.stops[0].lines[0].quantity").value(100))
                .andExpect(jsonPath("$.data.stops[0].lines[0].unitPrice").doesNotExist())
                .andExpect(jsonPath("$.data.stops[1].documentId").value(first))
                .andExpect(jsonPath("$.data.stops[1].totalTons").value(3))
                .andExpect(jsonPath("$.data.totalTons").value(5.5))
                // BR-DLV-04: vượt tải chỉ cảnh báo, vẫn lưu
                .andExpect(jsonPath("$.data.overloadTons").value(0.5))
                .andExpect(jsonPath("$.data.createdBy").exists());

        // Phiếu bán chưa bị trừ tồn, vẫn Nháp
        assertThat(stock(pigFeed, warehouseA)).isEqualTo(500);
        mockMvc.perform(get("/api/sales-orders/{id}", first)).andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("BR-DLV-05: đổi chỗ hai điểm giao (giữa chừng trùng thứ tự - UNIQUE hoãn kiểm tới COMMIT), bỏ một điểm, thêm điểm mới")
    void shouldReorderRemoveAndAddStops() throws Exception {
        long a = draft(farm, warehouseA, line(pigFeed, 10));
        long b = draft(dealer, warehouseA, line(chickenFeed, 10));
        long c = draft(farm, warehouseA, line(chickenFeed, 5));
        long tripId = data(createTrip(truckId, a, b)).get("id").asLong();

        updateTrip(tripId, truckId, b, a).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stops[0].documentId").value(b))
                .andExpect(jsonPath("$.data.stops[1].documentId").value(a));
        // Ép flush + kiểm constraint DEFERRABLE ngay trong test (bình thường chỉ kiểm lúc COMMIT)
        entityManager.flush();
        jdbcTemplate.execute("SET CONSTRAINTS ALL IMMEDIATE");
        jdbcTemplate.execute("SET CONSTRAINTS ALL DEFERRED");

        updateTrip(tripId, truckId, c, b).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stopCount").value(2))
                .andExpect(jsonPath("$.data.stops[0].documentId").value(c))
                .andExpect(jsonPath("$.data.stops[1].documentId").value(b));
        entityManager.flush();
        assertThat(count("SELECT count(*) FROM delivery_trip_stop WHERE trip_id = " + tripId)).isEqualTo(2);

        // Phiếu a đã bỏ khỏi chuyến → lại xếp được
        mockMvc.perform(get(TRIPS + "/assignable-orders"))
                .andExpect(jsonPath("$.data[?(@.documentId == " + a + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.documentId == " + b + ")]").doesNotExist());
    }

    @Test
    @DisplayName("BR-DLV-03: chỉ xếp phiếu Nháp; Chờ xác nhận / Hoàn tất bị từ chối 409, nêu mã phiếu")
    void shouldOnlyAssignDraftOrders() throws Exception {
        long pending = pendingOrder(farm, pigFeed, 10);
        long completed = draft(dealer, warehouseA, line(pigFeed, 10));
        mockMvc.perform(post("/api/sales-orders/{id}/complete", completed)).andExpect(status().isOk());

        createTrip(truckId, pending).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Chờ xác nhận")));
        createTrip(truckId, completed).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Hoàn tất")));
        // Phiếu không tồn tại / không phải phiếu bán → 404
        createTrip(truckId, 99_999_999L).andExpect(status().isNotFound());

        mockMvc.perform(get(TRIPS + "/assignable-orders"))
                .andExpect(jsonPath("$.data[?(@.documentId == " + pending + ")]").doesNotExist())
                .andExpect(jsonPath("$.data[?(@.documentId == " + completed + ")]").doesNotExist());
    }

    @Test
    @DisplayName("BR-DLV-03: một phiếu chỉ nằm trên một chuyến (409 nêu mã chuyến); trùng phiếu trong cùng request - 400")
    void shouldKeepOrderOnSingleTrip() throws Exception {
        long order = draft(farm, warehouseA, line(pigFeed, 10));
        String firstCode = data(createTrip(truckId, order)).get("code").asText();

        createTrip(truckId, order).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString(firstCode)));

        long other = draft(dealer, warehouseA, line(pigFeed, 5));
        createTrip(truckId, other, other).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("documentIds[1]"));
    }

    @Test
    @DisplayName("BR-DLV-02: validation - thiếu ngày / xe / lái xe → 400 từng field; xe, lái xe không tồn tại → 404")
    void shouldValidateTripRequest() throws Exception {
        mockMvc.perform(post(TRIPS).contentType(MediaType.APPLICATION_JSON).content("{\"documentIds\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.length()").value(3));

        mockMvc.perform(post(TRIPS).contentType(MediaType.APPLICATION_JSON).content(
                        tripBody(99_999_999L, driver.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(TRIPS).contentType(MediaType.APPLICATION_JSON).content(tripBody(truckId, 99_999_999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("BR-DLV-09: xóa chuyến Đang xếp - điểm giao mất theo, phiếu bán trở về chưa xếp chuyến")
    void shouldDeletePlannedTrip() throws Exception {
        long order = draft(farm, warehouseA, line(pigFeed, 10));
        long tripId = data(createTrip(truckId, order)).get("id").asLong();

        mockMvc.perform(delete(TRIPS + "/{id}", tripId)).andExpect(status().isNoContent());

        mockMvc.perform(get(TRIPS + "/{id}", tripId)).andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM delivery_trip_stop WHERE document_id = " + order)).isZero();
        createTrip(truckId, order).andExpect(status().isCreated());
    }

    // ===== Xuất chuyến =====

    @Test
    @DisplayName("BR-DLV-06: xuất chuyến - mọi phiếu Hoàn tất, trừ tồn đúng kho từng phiếu, ghi lịch sử kho; chuyến Đã xuất")
    void shouldDispatchTrip() throws Exception {
        long first = draft(farm, warehouseA, line(pigFeed, 30), line(chickenFeed, 20));
        long second = draft(dealer, warehouseB, line(pigFeed, 40));
        long tripId = data(createTrip(truckId, first, second)).get("id").asLong();
        int transactionsBefore = count("SELECT count(*) FROM stock_transaction");

        mockMvc.perform(post(TRIPS + "/{id}/dispatch", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.data.dispatchedAt").exists())
                .andExpect(jsonPath("$.data.dispatchedBy").exists())
                .andExpect(jsonPath("$.data.stops[0].documentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.stops[1].documentStatus").value("COMPLETED"));

        assertThat(stock(pigFeed, warehouseA)).isEqualTo(470);
        assertThat(stock(chickenFeed, warehouseA)).isEqualTo(480);
        assertThat(stock(pigFeed, warehouseB)).isEqualTo(460);
        assertThat(count("SELECT count(*) FROM stock_transaction")).isEqualTo(transactionsBefore + 3);
        // Phiếu bán hoàn tất qua chuyến giống hệt bấm Hoàn tất: phát sinh công nợ
        mockMvc.perform(get("/api/sales-orders/{id}", second))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.outstandingAmount").value(4000000));
    }

    @Test
    @DisplayName("BR-DLV-07: chuyến Đã xuất không sửa, không xóa, không xuất lại (409)")
    void shouldLockDispatchedTrip() throws Exception {
        long order = draft(farm, warehouseA, line(pigFeed, 10));
        long tripId = data(createTrip(truckId, order)).get("id").asLong();
        mockMvc.perform(post(TRIPS + "/{id}/dispatch", tripId)).andExpect(status().isOk());

        mockMvc.perform(post(TRIPS + "/{id}/dispatch", tripId)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("Đã xuất")));
        updateTrip(tripId, truckId, order).andExpect(status().isConflict());
        mockMvc.perform(delete(TRIPS + "/{id}", tripId)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("BR-DLV-06: thiếu hàng tính theo TỔNG cả chuyến (30 + 40 > 50) → 409 nêu từng phiếu; không phiếu nào bị trừ")
    void shouldRejectDispatchWhenTripTotalExceedsStock() throws Exception {
        jdbcTemplate.update("UPDATE inventory SET quantity = 50 WHERE product_id = ? AND warehouse_id = ?",
                pigFeed.getId(), warehouseA.getId());
        long first = draft(farm, warehouseA, line(chickenFeed, 10), line(pigFeed, 30));
        long second = draft(dealer, warehouseA, line(pigFeed, 40));
        long tripId = data(createTrip(truckId, first, second)).get("id").asLong();
        String firstCode = code(first);
        String secondCode = code(second);

        mockMvc.perform(post(TRIPS + "/{id}/dispatch", tripId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("còn 50, chuyến cần 70")))
                .andExpect(jsonPath("$.error.message").value(containsString(firstCode + ": 30")))
                .andExpect(jsonPath("$.error.message").value(containsString(secondCode + ": 40")));

        assertThat(stock(pigFeed, warehouseA)).isEqualTo(50);
        assertThat(stock(chickenFeed, warehouseA)).isEqualTo(500);
    }

    @Test
    @DisplayName("BR-DLV-07: phiếu trên chuyến bị hủy sau khi xếp - sửa chuyến vẫn được, xuất chuyến bị chặn và nêu mã phiếu")
    void shouldBlockDispatchWhenStopNoLongerDraft() throws Exception {
        long kept = draft(farm, warehouseA, line(pigFeed, 10));
        long cancelled = draft(dealer, warehouseA, line(pigFeed, 10));
        long tripId = data(createTrip(truckId, kept, cancelled)).get("id").asLong();
        mockMvc.perform(post("/api/sales-orders/{id}/cancel", cancelled).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isOk());
        String cancelledCode = code(cancelled);

        mockMvc.perform(get(TRIPS + "/{id}", tripId))
                .andExpect(jsonPath("$.data.stops[1].documentStatus").value("CANCELLED"));
        updateTrip(tripId, truckId, cancelled, kept).andExpect(status().isOk());
        mockMvc.perform(post(TRIPS + "/{id}/dispatch", tripId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString(cancelledCode)))
                .andExpect(jsonPath("$.error.message").value(containsString("Bỏ các phiếu này khỏi chuyến")));
        assertThat(stock(pigFeed, warehouseA)).isEqualTo(500);

        // Bỏ phiếu đã hủy ra → xuất được
        updateTrip(tripId, truckId, kept).andExpect(status().isOk());
        mockMvc.perform(post(TRIPS + "/{id}/dispatch", tripId)).andExpect(status().isOk());
        assertThat(stock(pigFeed, warehouseA)).isEqualTo(490);
    }

    @Test
    @DisplayName("BR-DLV-06: chuyến chưa có phiếu nào thì không xuất được")
    void shouldRejectEmptyDispatch() throws Exception {
        long tripId = data(createTrip(truckId)).get("id").asLong();
        mockMvc.perform(post(TRIPS + "/{id}/dispatch", tripId)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("chưa có phiếu")));
    }

    @Test
    @DisplayName("Danh sách chuyến: số điểm, tổng tấn, lọc theo trạng thái; lái xe chỉ gồm nhân viên đang làm")
    void shouldListTripsAndDrivers() throws Exception {
        long order = draft(farm, warehouseA, line(pigFeed, 40));    // 1 tấn
        String code = data(createTrip(truckId, order)).get("code").asText();

        mockMvc.perform(get(TRIPS).param("keyword", code))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].stopCount").value(1))
                .andExpect(jsonPath("$.data.items[0].totalTons").value(1))
                .andExpect(jsonPath("$.data.items[0].overloadTons").doesNotExist());
        mockMvc.perform(get(TRIPS).param("keyword", code).param("status", "DISPATCHED"))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        Employee resigned = saveEmployee("Lái xe đã nghỉ");
        resigned.setActive(false);
        employeeRepository.saveAndFlush(resigned);
        mockMvc.perform(get(TRIPS + "/drivers"))
                .andExpect(jsonPath("$.data[?(@.id == " + driver.getId() + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.id == " + resigned.getId() + ")]").doesNotExist());
    }

    // ===== Helpers =====

    private ResultActions createVehicle(String plate, String name, String capacity) throws Exception {
        String body = "{\"plateNumber\": " + objectMapper.writeValueAsString(plate)
                + (name == null ? "" : ", \"name\": " + objectMapper.writeValueAsString(name))
                + (capacity == null ? "" : ", \"capacityTons\": " + capacity) + "}";
        return mockMvc.perform(post(VEHICLES).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions createTrip(long vehicleId, Long... documentIds) throws Exception {
        return mockMvc.perform(post(TRIPS).contentType(MediaType.APPLICATION_JSON)
                .content(tripBody(vehicleId, driver.getId(), documentIds)));
    }

    private ResultActions updateTrip(long tripId, long vehicleId, Long... documentIds) throws Exception {
        return mockMvc.perform(put(TRIPS + "/{id}", tripId).contentType(MediaType.APPLICATION_JSON)
                .content(tripBody(vehicleId, driver.getId(), documentIds)));
    }

    private static String tripBody(long vehicleId, long driverId, Long... documentIds) {
        return "{\"tripDate\": \"2026-11-04\", \"vehicleId\": " + vehicleId + ", \"driverId\": " + driverId
                + ", \"documentIds\": [" + Arrays.stream(documentIds).map(String::valueOf)
                .collect(Collectors.joining(", ")) + "]}";
    }

    private long draft(Customer customer, Warehouse warehouse, String... lines) throws Exception {
        String body = "{\"customerId\": " + customer.getId() + ", \"warehouseId\": " + warehouse.getId()
                + ", \"lines\": [" + String.join(", ", lines) + "]}";
        return data(mockMvc.perform(post("/api/sales-orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long pendingOrder(Customer customer, Product product, int quantity) throws Exception {
        String body = "{\"customerId\": " + customer.getId() + ", \"orderChannel\": \"PHONE\", \"lines\": [{\"productId\": "
                + product.getId() + ", \"quantity\": " + quantity + "}]}";
        return data(mockMvc.perform(post("/api/sales-orders/pending").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isCreated())).get("id").asLong();
    }

    // Giá 100.000 đ/bao cho mọi dòng — chuyến giao không quan tâm giá
    private static String line(Product product, int quantity) {
        return "{\"productId\": " + product.getId() + ", \"quantity\": " + quantity + ", \"unitPrice\": 100000}";
    }

    private String code(long documentId) throws Exception {
        return data(mockMvc.perform(get("/api/sales-orders/{id}", documentId))).get("code").asText();
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }

    private void setStock(Product product, Warehouse warehouse, int quantity) {
        jdbcTemplate.update("INSERT INTO inventory (product_id, warehouse_id, quantity) VALUES (?, ?, ?)",
                product.getId(), warehouse.getId(), quantity);
    }

    private Integer stock(Product product, Warehouse warehouse) {
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

    private Warehouse saveWarehouse(String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setName(name);
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Địa chỉ test");
        return warehouseRepository.save(warehouse);
    }

    private Customer saveCustomer(String name, String phone, String address) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setType(CustomerType.DEALER);
        customer.setPhone(phone);
        customer.setAddress(address);
        return customerRepository.save(customer);
    }

    private Employee saveEmployee(String name) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setDailyWage(new BigDecimal("300000"));
        return employeeRepository.save(employee);
    }
}
