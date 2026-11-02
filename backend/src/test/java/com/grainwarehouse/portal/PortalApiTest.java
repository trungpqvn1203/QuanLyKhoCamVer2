package com.grainwarehouse.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grainwarehouse.auth.entity.Role;
import com.grainwarehouse.auth.repository.AppUserRepository;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import com.grainwarehouse.sales.dto.SalesOrderLineRequest;
import com.grainwarehouse.sales.dto.SalesOrderRequest;
import com.grainwarehouse.sales.service.SalesOrderService;
import com.grainwarehouse.support.MutableClock;
import com.grainwarehouse.support.SecurityTestSupport;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.entity.WarehouseType;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * App khách (ADR-0005, BR-POR-01..10) qua API với MockMvc "trần": cookie và CSRF thật như trình duyệt.
 * Trọng tâm: hai vùng đăng nhập không lẫn nhau, khách chỉ thấy đơn của mình, giá chỉ hiện sau khi xác nhận.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PortalApiTest {

    private static final String PASSWORD = "matkhau-cua-hang-1";
    private static final String NEW_PASSWORD = "matkhau-khach-moi-2";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now());
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductUnitRepository unitRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private SecurityTestSupport.Session staff;
    private Customer dealer;
    private Customer farm;
    private Product pigFeed;
    private Product chickenFeed;
    private Warehouse warehouse;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.now());
        mockMvc = SecurityTestSupport.rawMockMvc(context);
        SecurityTestSupport.createUser(userRepository, passwordEncoder, "lan.bh", true, Role.SALES_STAFF);
        SecurityTestSupport.createUser(userRepository, passwordEncoder, "kho.qm", true, Role.WAREHOUSE_MANAGER);
        staff = SecurityTestSupport.login(mockMvc, "lan.bh");
        dealer = saveCustomer("Đại lý Minh Phát", "0901000101");
        farm = saveCustomer("Trại heo Hưng Thịnh", "0901000102");
        Category category = new Category();
        category.setName("Cám test app");
        categoryRepository.save(category);
        pigFeed = saveProduct(category, "Cám heo con app");
        chickenFeed = saveProduct(category, "Cám gà thịt app");
        warehouse = new Warehouse();
        warehouse.setName("Kho app");
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Địa chỉ test");
        warehouseRepository.save(warehouse);
    }

    // ===== Tài khoản app (phía cửa hàng) =====

    @Test
    @DisplayName("BR-POR-02: bán hàng tạo tài khoản app - 201, phải đổi mật khẩu; trùng - 409; quản lý kho không tạo được - 403")
    void staffShouldCreatePortalAccount() throws Exception {
        staffCreateAccount(dealer, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.exists").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
        staffCreateAccount(dealer, PASSWORD).andExpect(status().isConflict());

        SecurityTestSupport.Session warehouseManager = SecurityTestSupport.login(mockMvc, "kho.qm");
        mockMvc.perform(post("/api/customers/{id}/portal-account", farm.getId()).with(csrf())
                        .cookie(warehouseManager.access()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden());
        // Quản lý kho vẫn XEM được (như xem khách hàng)
        mockMvc.perform(get("/api/customers/{id}/portal-account", farm.getId()).cookie(warehouseManager.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.active").doesNotExist());
    }

    @Test
    @DisplayName("BR-POR-02: khách ngừng hoạt động không tạo tài khoản được - 409; mật khẩu ngắn - 400")
    void shouldValidateAccountCreation() throws Exception {
        staffCreateAccount(dealer, "ngan").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("password"));
        deactivate(farm);
        staffCreateAccount(farm, PASSWORD).andExpect(status().isConflict());
    }

    // ===== Đăng nhập =====

    @Test
    @DisplayName("BR-POR-01: đăng nhập bằng SĐT (gõ có dấu cách cũng được) - cookie GW_CAT/GW_CRT HttpOnly, đúng Path, không token trong body")
    void shouldLoginWithPhone() throws Exception {
        staffCreateAccount(dealer, PASSWORD).andExpect(status().isCreated());

        MvcResult result = portalLogin("0901 000 101", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerName").value("Đại lý Minh Phát"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andReturn();
        MockHttpServletResponse response = result.getResponse();
        Cookie access = response.getCookie("GW_CAT");
        Cookie refresh = response.getCookie("GW_CRT");
        assertThat(access).isNotNull();
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getPath()).isEqualTo("/api/portal");
        assertThat(refresh.getPath()).isEqualTo("/api/portal/auth");
        assertThat(response.getCookie("GW_AT")).isNull();
        assertThat(response.getContentAsString()).doesNotContain(access.getValue()).doesNotContain(refresh.getValue());
    }

    @Test
    @DisplayName("BR-POR-04: sai SĐT và sai mật khẩu cùng một thông báo")
    void shouldNotRevealWhichPhoneHasAccount() throws Exception {
        staffCreateAccount(dealer, PASSWORD).andExpect(status().isCreated());

        String wrongPassword = portalLogin(dealer.getPhone(), "sai-mat-khau-1").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknownPhone = portalLogin("0999999999", PASSWORD).andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(message(wrongPassword)).isEqualTo(message(unknownPhone));
    }

    @Test
    @DisplayName("BR-POR-04: sai 5 lần liên tiếp - khóa 15 phút kể cả khi mật khẩu đúng; hết 15 phút thì đăng nhập được")
    void shouldLockAfterFiveFailures() throws Exception {
        staffCreateAccount(dealer, PASSWORD).andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            portalLogin(dealer.getPhone(), "sai-mat-khau-" + i).andExpect(status().isUnauthorized());
        }

        portalLogin(dealer.getPhone(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value(containsString("Thử lại sau")));

        clock.advance(Duration.ofMinutes(16));
        portalLogin(dealer.getPhone(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("BR-POR-10: cửa hàng khóa tài khoản - đúng mật khẩu vẫn 401, gia hạn bị từ chối; mở khóa thì đăng nhập lại được")
    void lockedAccountShouldNotLoginOrRefresh() throws Exception {
        staffCreateAccount(dealer, PASSWORD).andExpect(status().isCreated());
        Session session = Session.from(portalLogin(dealer.getPhone(), PASSWORD).andReturn());

        staffAction(dealer, "lock").andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));

        portalLogin(dealer.getPhone(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value(containsString("bị khóa")));
        mockMvc.perform(post("/api/portal/auth/refresh").with(csrf()).cookie(session.refresh()))
                .andExpect(status().isUnauthorized());
        // Access token còn hạn nhưng mọi API đơn kiểm lại tài khoản ở DB
        mockMvc.perform(get("/api/portal/orders").cookie(session.access()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_DISABLED"));

        staffAction(dealer, "unlock").andExpect(status().isOk());
        portalLogin(dealer.getPhone(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("BR-POR-05: gia hạn xoay vòng token; dùng lại token cũ sau thời gian ân hạn - thu hồi cả phiên")
    void refreshShouldRotateAndDetectReuse() throws Exception {
        staffCreateAccount(dealer, PASSWORD).andExpect(status().isCreated());
        Session first = Session.from(portalLogin(dealer.getPhone(), PASSWORD).andReturn());

        MvcResult renewed = mockMvc.perform(post("/api/portal/auth/refresh").with(csrf()).cookie(first.refresh()))
                .andExpect(status().isOk()).andReturn();
        Session second = Session.from(renewed);
        assertThat(second.refresh().getValue()).isNotEqualTo(first.refresh().getValue());

        clock.advance(Duration.ofMinutes(1));
        mockMvc.perform(post("/api/portal/auth/refresh").with(csrf()).cookie(first.refresh()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/portal/auth/refresh").with(csrf()).cookie(second.refresh()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("BR-POR-05: phiên khách sống 30 ngày không dùng (nhân viên chỉ 8 giờ)")
    void refreshShouldLastThirtyDaysIdle() throws Exception {
        staffCreateAccount(dealer, PASSWORD).andExpect(status().isCreated());
        Session session = Session.from(portalLogin(dealer.getPhone(), PASSWORD).andReturn());

        clock.advance(Duration.ofDays(29));
        MvcResult renewed = mockMvc.perform(post("/api/portal/auth/refresh").with(csrf()).cookie(session.refresh()))
                .andExpect(status().isOk()).andReturn();

        clock.advance(Duration.ofDays(31));
        mockMvc.perform(post("/api/portal/auth/refresh").with(csrf()).cookie(Session.from(renewed).refresh()))
                .andExpect(status().isUnauthorized());
    }

    // ===== Hai vùng đăng nhập tách biệt (ADR-0005 mục 1) =====

    @Test
    @DisplayName("ADR-0005: token khách đặt vào cookie nhân viên GW_AT - mọi API nhân viên 401")
    void customerTokenShouldNotWorkOnStaffApi() throws Exception {
        Session customer = readyCustomer(dealer);
        Cookie forged = new Cookie("GW_AT", customer.access().getValue());

        for (String url : List.of("/api/customers", "/api/sales-orders", "/api/inventory/products",
                "/api/reports/dashboard", "/api/auth/me")) {
            mockMvc.perform(get(url).cookie(forged)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("ADR-0005: phiên nhân viên (kể cả token nhân viên đặt vào GW_CAT) không gọi được API khách - 401")
    void staffTokenShouldNotWorkOnPortalApi() throws Exception {
        mockMvc.perform(get("/api/portal/me").cookie(staff.access())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portal/me").cookie(new Cookie("GW_CAT", staff.access().getValue())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/portal/products")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ADR-0005: ghi qua API khách không có CSRF - 403")
    void portalWritesShouldRequireCsrf() throws Exception {
        Session customer = readyCustomer(dealer);

        mockMvc.perform(post("/api/portal/orders").cookie(customer.access()).contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(null, line(pigFeed, 5))))
                .andExpect(status().isForbidden());
    }

    // ===== Đặt hàng =====

    @Test
    @DisplayName("BR-POR-03: chưa đổi mật khẩu cửa hàng cấp - đặt hàng 403; đổi xong thì đặt được")
    void shouldRequirePasswordChangeBeforeOrdering() throws Exception {
        staffCreateAccount(dealer, PASSWORD).andExpect(status().isCreated());
        Session session = Session.from(portalLogin(dealer.getPhone(), PASSWORD).andReturn());

        createOrder(session, orderBody(null, line(pigFeed, 5)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(put("/api/portal/me/password").with(csrf()).cookie(session.access(), session.refresh())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/portal/me").cookie(session.access()))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));
        createOrder(session, orderBody(null, line(pigFeed, 5))).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("BR-POR-06: khách chỉ thấy SP đang kinh doanh, không có giá và tồn")
    void productsShouldHidePriceAndStock() throws Exception {
        Session customer = readyCustomer(dealer);
        deactivateProduct(chickenFeed);

        String body = mockMvc.perform(get("/api/portal/products").cookie(customer.access()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode products = objectMapper.readTree(body).get("data");
        List<String> names = products.findValuesAsText("name");
        assertThat(names).contains("Cám heo con app").doesNotContain("Cám gà thịt app");
        assertThat(body).doesNotContain("unitPrice").doesNotContain("stock").doesNotContain("Quantity");
    }

    @Test
    @DisplayName("BR-POR-07: khách đặt - đơn Chờ xác nhận kênh App của đúng khách trong token, chưa có giá; cửa hàng thấy trong danh sách")
    void customerOrderShouldBecomePendingAppOrder() throws Exception {
        Session customer = readyCustomer(dealer);
        LocalDate nextWeek = LocalDate.now().plusDays(7);

        JsonNode order = data(createOrder(customer, orderBody(nextWeek, line(pigFeed, 50)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.orderChannel").value("APP"))
                .andExpect(jsonPath("$.data.editable").value(true))
                .andExpect(jsonPath("$.data.requestedDeliveryDate").value(nextWeek.toString()))
                .andExpect(jsonPath("$.data.requestLines[0].quantity").value(50))
                .andExpect(jsonPath("$.data.lines.length()").value(0))
                .andExpect(jsonPath("$.data.totalAmount").doesNotExist())
                // Thông tin nội bộ không ra khỏi API khách
                .andExpect(jsonPath("$.data.warehouseName").doesNotExist())
                .andExpect(jsonPath("$.data.createdBy").doesNotExist())
                .andExpect(jsonPath("$.data.paidAmount").doesNotExist()));

        long id = order.get("id").asLong();
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT customer_id FROM stock_document WHERE id = ?", Long.class, id))
                .isEqualTo(dealer.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT created_by FROM stock_document WHERE id = ?", String.class, id))
                .isEqualTo("app:" + dealer.getCode());
        mockMvc.perform(get("/api/sales-orders").cookie(staff.access()).param("status", "PENDING")
                        .param("orderChannel", "APP").param("customerId", dealer.getId().toString()))
                .andExpect(jsonPath("$.data.items[0].id").value(id));
    }

    @Test
    @DisplayName("BR-POR-08: khách B không xem / sửa / hủy được đơn của khách A - 404; danh sách chỉ có đơn của mình")
    void customersShouldOnlySeeTheirOwnOrders() throws Exception {
        Session customerA = readyCustomer(dealer);
        Session customerB = readyCustomer(farm);
        long orderOfA = data(createOrder(customerA, orderBody(null, line(pigFeed, 5)))).get("id").asLong();

        mockMvc.perform(get("/api/portal/orders/{id}", orderOfA).cookie(customerB.access()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/portal/orders/{id}", orderOfA).with(csrf()).cookie(customerB.access())
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(null, line(pigFeed, 1))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/portal/orders/{id}/cancel", orderOfA).with(csrf()).cookie(customerB.access()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/portal/orders").cookie(customerB.access()))
                .andExpect(jsonPath("$.data.items.length()").value(0));
        mockMvc.perform(get("/api/portal/orders").cookie(customerA.access()))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    @DisplayName("BR-POR-08, 09: cửa hàng xác nhận - khách thấy SL + giá + tổng, không sửa / hủy trên app được nữa")
    void customerShouldSeePricesOnlyAfterConfirmation() throws Exception {
        Session customer = readyCustomer(dealer);
        long id = data(createOrder(customer, orderBody(null, line(pigFeed, 50)))).get("id").asLong();

        // Cửa hàng chốt: đổi sang cám gà 40 bao, 250.000 đ/bao
        salesOrderService.confirm(id, new SalesOrderRequest(dealer.getId(), warehouse.getId(), null, null,
                List.of(new SalesOrderLineRequest(chickenFeed.getId(), 40, new BigDecimal("250000")))), "lan.bh");

        mockMvc.perform(get("/api/portal/orders/{id}", id).cookie(customer.access()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.editable").value(false))
                .andExpect(jsonPath("$.data.requestLines[0].productId").value(pigFeed.getId()))
                .andExpect(jsonPath("$.data.lines[0].productId").value(chickenFeed.getId()))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(40))
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(250000))
                .andExpect(jsonPath("$.data.totalAmount").value(10000000));
        mockMvc.perform(put("/api/portal/orders/{id}", id).with(csrf()).cookie(customer.access())
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(null, line(pigFeed, 60))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("gọi cửa hàng")));
        mockMvc.perform(post("/api/portal/orders/{id}/cancel", id).with(csrf()).cookie(customer.access()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("BR-POR-09: khách sửa rồi tự hủy đơn Chờ xác nhận - lý do 'Khách hủy qua app' kèm ghi chú")
    void customerShouldEditAndCancelPendingOrder() throws Exception {
        Session customer = readyCustomer(dealer);
        long id = data(createOrder(customer, orderBody(null, line(pigFeed, 50)))).get("id").asLong();

        mockMvc.perform(put("/api/portal/orders/{id}", id).with(csrf()).cookie(customer.access())
                        .contentType(MediaType.APPLICATION_JSON).content(orderBody(null, line(chickenFeed, 30))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestLines.length()").value(1))
                .andExpect(jsonPath("$.data.requestLines[0].productId").value(chickenFeed.getId()))
                .andExpect(jsonPath("$.data.orderChannel").value("APP"));

        mockMvc.perform(post("/api/portal/orders/{id}/cancel", id).with(csrf()).cookie(customer.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"Đặt nhầm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("Khách hủy qua app: Đặt nhầm"));
    }

    @Test
    @DisplayName("BR-POR-10: cửa hàng đặt lại mật khẩu - mọi phiên của khách bị thu hồi, khách phải đổi lại mật khẩu")
    void resetPasswordShouldRevokeSessions() throws Exception {
        Session customer = readyCustomer(dealer);

        mockMvc.perform(put("/api/customers/{id}/portal-account/password", dealer.getId()).with(csrf())
                        .cookie(staff.access()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));

        mockMvc.perform(post("/api/portal/auth/refresh").with(csrf()).cookie(customer.refresh()))
                .andExpect(status().isUnauthorized());
        portalLogin(dealer.getPhone(), NEW_PASSWORD).andExpect(status().isUnauthorized());
        portalLogin(dealer.getPhone(), PASSWORD).andExpect(jsonPath("$.data.mustChangePassword").value(true));
    }

    // ===== Helpers =====

    /** Cookie app khách sau một lần đăng nhập / gia hạn. */
    private record Session(Cookie access, Cookie refresh) {
        static Session from(MvcResult result) {
            return new Session(result.getResponse().getCookie("GW_CAT"), result.getResponse().getCookie("GW_CRT"));
        }
    }

    /** Khách có tài khoản, đã đổi mật khẩu cửa hàng cấp, đang đăng nhập. */
    private Session readyCustomer(Customer customer) throws Exception {
        staffCreateAccount(customer, PASSWORD).andExpect(status().isCreated());
        Session session = Session.from(portalLogin(customer.getPhone(), PASSWORD).andReturn());
        mockMvc.perform(put("/api/portal/me/password").with(csrf()).cookie(session.access(), session.refresh())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + PASSWORD + "\", \"newPassword\": \"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        return session;
    }

    private ResultActions staffCreateAccount(Customer customer, String password) throws Exception {
        return mockMvc.perform(post("/api/customers/{id}/portal-account", customer.getId()).with(csrf())
                .cookie(staff.access()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"" + password + "\"}"));
    }

    private ResultActions staffAction(Customer customer, String action) throws Exception {
        return mockMvc.perform(post("/api/customers/{id}/portal-account/" + action, customer.getId()).with(csrf())
                .cookie(staff.access()));
    }

    private ResultActions portalLogin(String phone, String password) throws Exception {
        return mockMvc.perform(post("/api/portal/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"" + phone + "\", \"password\": \"" + password + "\"}"));
    }

    private ResultActions createOrder(Session session, String body) throws Exception {
        return mockMvc.perform(post("/api/portal/orders").with(csrf()).cookie(session.access())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String orderBody(LocalDate deliveryDate, String... lines) {
        String dateField = deliveryDate == null ? "" : "\"requestedDeliveryDate\": \"" + deliveryDate + "\", ";
        return "{" + dateField + "\"lines\": [" + String.join(", ", lines) + "]}";
    }

    private static String line(Product product, int quantity) {
        return "{\"productId\": " + product.getId() + ", \"quantity\": " + quantity + "}";
    }

    private JsonNode data(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
    }

    private String message(String body) throws Exception {
        return objectMapper.readTree(body).get("error").get("message").asText();
    }

    // Ghi thẳng DB rồi clear để Hibernate không giữ bản "đang hoạt động" cũ
    private void deactivate(Customer customer) {
        entityManager.flush();
        jdbcTemplate.update("UPDATE customer SET active = FALSE WHERE id = ?", customer.getId());
        entityManager.clear();
    }

    private void deactivateProduct(Product product) {
        entityManager.flush();
        jdbcTemplate.update("UPDATE product SET active = FALSE WHERE id = ?", product.getId());
        entityManager.clear();
    }

    private Customer saveCustomer(String name, String phone) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setType(CustomerType.DEALER);
        customer.setPhone(phone);
        Customer saved = customerRepository.saveAndFlush(customer);
        // code do DB sinh (DEFAULT) → đọc lại để có mã khách
        entityManager.refresh(saved);
        return saved;
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
