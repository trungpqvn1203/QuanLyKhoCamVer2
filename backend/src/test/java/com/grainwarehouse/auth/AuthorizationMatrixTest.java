package com.grainwarehouse.auth;

import com.grainwarehouse.auth.entity.Role;
import com.grainwarehouse.support.SecurityTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import static com.grainwarehouse.auth.entity.Role.SALES_STAFF;
import static com.grainwarehouse.auth.entity.Role.SYSTEM_ADMIN;
import static com.grainwarehouse.auth.entity.Role.WAREHOUSE_MANAGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ma trận phân quyền ADR-0003 mục 5 — mỗi ô một ca test, cho từng vai trò.
 * "Được phép" = KHÔNG bị 401/403; endpoint có thể trả 200/400/404... (body rỗng, id không tồn tại) — điều cần kiểm
 * chỉ là request đi QUA được lớp bảo mật tới controller. "Bị cấm" = 403 và service không chạy.
 * Transaction rollback: request được phép có ghi gì vào DB cũng bị hoàn tác.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthorizationMatrixTest {

    private static final Set<Role> ALL = EnumSet.allOf(Role.class);
    private static final Set<Role> WAREHOUSE = EnumSet.of(WAREHOUSE_MANAGER, SYSTEM_ADMIN);
    private static final Set<Role> SALES = EnumSet.of(SALES_STAFF, SYSTEM_ADMIN);
    private static final Set<Role> ADMIN_ONLY = EnumSet.of(SYSTEM_ADMIN);

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = SecurityTestSupport.rawMockMvc(context);
    }

    record Rule(HttpMethod method, String path, Set<Role> allowed) {
        @Override
        public String toString() {
            return method + " " + path;
        }
    }

    static Stream<Rule> rules() {
        return Stream.of(
                // Danh mục, SP, kho: ai cũng xem; chỉ quản lý hệ thống ghi
                new Rule(HttpMethod.GET, "/api/products", ALL),
                new Rule(HttpMethod.GET, "/api/products/1", ALL),
                new Rule(HttpMethod.POST, "/api/products", ADMIN_ONLY),
                new Rule(HttpMethod.DELETE, "/api/products/999999", ADMIN_ONLY),
                new Rule(HttpMethod.GET, "/api/categories", ALL),
                new Rule(HttpMethod.POST, "/api/categories", ADMIN_ONLY),
                new Rule(HttpMethod.GET, "/api/warehouses", ALL),
                new Rule(HttpMethod.PUT, "/api/warehouses/999999", ADMIN_ONLY),
                // Nhà cung cấp (kể cả xem NCC của một SP), phiếu nhập: quản lý kho
                new Rule(HttpMethod.GET, "/api/products/1/suppliers", WAREHOUSE),
                new Rule(HttpMethod.GET, "/api/suppliers", WAREHOUSE),
                new Rule(HttpMethod.POST, "/api/suppliers", WAREHOUSE),
                new Rule(HttpMethod.POST, "/api/suppliers/999999/products", WAREHOUSE),
                new Rule(HttpMethod.GET, "/api/inbound-receipts", WAREHOUSE),
                new Rule(HttpMethod.POST, "/api/inbound-receipts", WAREHOUSE),
                new Rule(HttpMethod.POST, "/api/inbound-receipts/999999/complete", WAREHOUSE),
                // Trả tiền NCC, công nợ NCC (BR-SPAY-06): quản lý kho
                new Rule(HttpMethod.POST, "/api/inbound-receipts/999999/payments", WAREHOUSE),
                new Rule(HttpMethod.POST, "/api/inbound-receipts/999999/payments/1/reverse", WAREHOUSE),
                new Rule(HttpMethod.GET, "/api/supplier-debts", WAREHOUSE),
                // Khách hàng, bảng giá, phiếu bán: ai cũng xem; bán hàng ghi
                new Rule(HttpMethod.GET, "/api/customers", ALL),
                new Rule(HttpMethod.GET, "/api/customers/999999/prices", ALL),
                new Rule(HttpMethod.POST, "/api/customers", SALES),
                new Rule(HttpMethod.PUT, "/api/customers/999999/prices/1", SALES),
                new Rule(HttpMethod.GET, "/api/sales-orders", ALL),
                new Rule(HttpMethod.POST, "/api/sales-orders", SALES),
                new Rule(HttpMethod.POST, "/api/sales-orders/999999/complete", SALES),
                new Rule(HttpMethod.POST, "/api/sales-orders/999999/payments", SALES),
                new Rule(HttpMethod.GET, "/api/customer-debts", SALES),
                // Tồn kho, lịch sử kho: ai cũng xem
                new Rule(HttpMethod.GET, "/api/inventory/products", ALL),
                new Rule(HttpMethod.GET, "/api/inventory/transactions", ALL),
                // Nhân viên, lương, người dùng: quản lý hệ thống; chấm công: quản lý kho
                new Rule(HttpMethod.GET, "/api/attendance", WAREHOUSE),
                new Rule(HttpMethod.PUT, "/api/attendance/999999/2026-10-01", WAREHOUSE),
                new Rule(HttpMethod.GET, "/api/employees", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/employees", ADMIN_ONLY),
                // Tạm ứng, trả lương, công nợ nhân viên (BR-EPAY-06): chỉ quản lý hệ thống
                new Rule(HttpMethod.GET, "/api/employees/999999/advances", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/employees/999999/advances", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/employees/999999/advances/1/reverse", ADMIN_ONLY),
                new Rule(HttpMethod.GET, "/api/payrolls/2026-10/items/999999", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/payrolls/2026-10/items/999999/payments", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/payrolls/2026-10/items/999999/payments/1/reverse", ADMIN_ONLY),
                new Rule(HttpMethod.GET, "/api/employee-debts", ADMIN_ONLY),
                // Sổ quỹ, phiếu thu/chi khác, danh mục thu/chi (BR-CASH-07): chỉ quản lý hệ thống
                new Rule(HttpMethod.GET, "/api/cash-book/summary", ADMIN_ONLY),
                new Rule(HttpMethod.GET, "/api/cash-book?account=CASH", ADMIN_ONLY),
                new Rule(HttpMethod.PUT, "/api/cash-book/opening", ADMIN_ONLY),
                new Rule(HttpMethod.GET, "/api/cash-vouchers", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/cash-vouchers", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/cash-vouchers/999999/reverse", ADMIN_ONLY),
                new Rule(HttpMethod.GET, "/api/cash-categories", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/cash-categories", ADMIN_ONLY),
                new Rule(HttpMethod.GET, "/api/payrolls/2026-10", ADMIN_ONLY),
                new Rule(HttpMethod.GET, "/api/users", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/users", ADMIN_ONLY),
                // Nhập Excel (BR-XLS-06): quyền như tạo tay của từng loại; file mẫu không chứa dữ liệu nhạy cảm
                // nên tải mẫu theo quyền xem
                new Rule(HttpMethod.GET, "/api/products/import/template", ALL),
                new Rule(HttpMethod.POST, "/api/products/import/preview", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/products/import", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/customers/import/preview", SALES),
                new Rule(HttpMethod.POST, "/api/customers/import", SALES),
                new Rule(HttpMethod.POST, "/api/suppliers/import", WAREHOUSE),
                new Rule(HttpMethod.GET, "/api/employees/import/template", ADMIN_ONLY),
                new Rule(HttpMethod.POST, "/api/employees/import", ADMIN_ONLY));
    }

    static Stream<Arguments> ruleAndRole() {
        return rules().flatMap(rule -> Arrays.stream(Role.values()).map(role -> Arguments.of(rule, role)));
    }

    @ParameterizedTest(name = "{0} với {1}")
    @MethodSource("ruleAndRole")
    @DisplayName("Ma trận phân quyền: đúng vai trò thì qua, sai vai trò thì 403")
    void shouldEnforceMatrix(Rule rule, Role role) throws Exception {
        int status = mockMvc.perform(as(rule, role)).andReturn().getResponse().getStatus();
        if (rule.allowed().contains(role)) {
            assertThat(status).as("%s phải được %s", role, rule).isNotIn(401, 403);
        } else {
            assertThat(status).as("%s phải bị chặn ở %s", role, rule).isEqualTo(403);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rules")
    @DisplayName("Chưa đăng nhập → 401 ở mọi endpoint nghiệp vụ")
    void shouldRequireLogin(Rule rule) throws Exception {
        mockMvc.perform(withBody(request(rule.method(), rule.path())).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Kiêm nhiều vai trò: quyền = hợp quyền các vai trò")
    void shouldUnionRolesOfMultiRoleUser() throws Exception {
        var both = jwt().authorities(new SimpleGrantedAuthority("ROLE_WAREHOUSE_MANAGER"),
                new SimpleGrantedAuthority("ROLE_SALES_STAFF"));
        mockMvc.perform(request(HttpMethod.GET, "/api/suppliers").with(both)).andExpect(status().isOk());
        mockMvc.perform(request(HttpMethod.GET, "/api/customer-debts").with(both)).andExpect(status().isOk());
        mockMvc.perform(request(HttpMethod.GET, "/api/employees").with(both)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Công khai: health check không cần đăng nhập; actuator khác và URL không khai báo quyền bị chặn")
    void shouldExposeOnlyPublicEndpoints() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/api/health")).andExpect(status().isOk());
        mockMvc.perform(request(HttpMethod.GET, "/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(request(HttpMethod.GET, "/actuator/info")).andExpect(status().isUnauthorized());
        // denyAll: kể cả quản lý hệ thống cũng không vào được URL chưa khai báo trong ma trận
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
        mockMvc.perform(request(HttpMethod.GET, "/actuator/info").with(admin)).andExpect(status().isForbidden());
        mockMvc.perform(request(HttpMethod.GET, "/api/chua-khai-bao").with(admin)).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder as(Rule rule, Role role) {
        return withBody(request(rule.method(), rule.path()))
                .with(csrf())
                .with(jwt().jwt(token -> token.subject("0"))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    // Body rỗng: request được phép sẽ dừng ở validation (400) — đủ để biết đã qua lớp bảo mật
    private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder) {
        return builder.contentType(MediaType.APPLICATION_JSON).content("{}");
    }
}
