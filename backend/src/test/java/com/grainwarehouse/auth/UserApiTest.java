package com.grainwarehouse.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grainwarehouse.auth.dto.LoginRequest;
import com.grainwarehouse.auth.entity.AppUser;
import com.grainwarehouse.auth.entity.Role;
import com.grainwarehouse.auth.repository.AppUserRepository;
import com.grainwarehouse.auth.service.AuthService;
import com.grainwarehouse.support.SecurityTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Quản lý tài khoản (BR-AUTH-01..07). Gọi với tư cách SYSTEM_ADMIN mặc định của DefaultTestUserConfig. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserApiTest {

    private static final String BASE = "/api/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private AppUser admin;

    @BeforeEach
    void setUp() {
        admin = SecurityTestSupport.createUser(userRepository, passwordEncoder, "quan.tri", true, Role.SYSTEM_ADMIN);
    }

    @Test
    @DisplayName("BR-AUTH-01, 02: tạo tài khoản - 201, tên đăng nhập chuẩn hóa chữ thường, nhiều vai trò, không trả mật khẩu băm")
    void shouldCreateUser() throws Exception {
        create("  Lan.BH ", "Nguyễn Thị Lan", "matkhau123", "SALES_STAFF", "WAREHOUSE_MANAGER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("lan.bh"))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Thị Lan"))
                .andExpect(jsonPath("$.data.roles[0]").value("WAREHOUSE_MANAGER"))
                .andExpect(jsonPath("$.data.roles[1]").value("SALES_STAFF"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.lastLoginAt").doesNotExist())
                .andExpect(content().string(not(containsString("password"))));

        entityManager.flush();
        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM app_user WHERE username = 'lan.bh'", String.class);
        assertThat(hash).startsWith("{bcrypt}$2a$").doesNotContain("matkhau123");
    }

    @Test
    @DisplayName("BR-AUTH-01: trùng tên đăng nhập (kể cả khác hoa thường) → 409")
    void shouldRejectDuplicateUsername() throws Exception {
        create("lan.bh", "Lan", "matkhau123", "SALES_STAFF").andExpect(status().isCreated());
        create("LAN.BH", "Lan 2", "matkhau123", "SALES_STAFF")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("BR-AUTH-01, 03: sai định dạng tên, mật khẩu ngắn / quá 72 byte, không vai trò, vai trò lạ → 400 đúng field")
    void shouldValidateUser() throws Exception {
        create("lan bh", "Lan", "matkhau123", "SALES_STAFF")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("username"));
        create("lan", "Lan", "ngan", "SALES_STAFF")
                .andExpect(jsonPath("$.error.details[0].field").value("password"));
        // 30 chữ "ệ" = 90 byte UTF-8 > 72 byte BCrypt dùng được
        create("lan", "Lan", "ệ".repeat(30), "SALES_STAFF")
                .andExpect(jsonPath("$.error.details[0].field").value("password"));
        create("lan", "Lan", "matkhau123")
                .andExpect(jsonPath("$.error.details[0].field").value("roles"));
        create("lan", "Lan", "matkhau123", "KE_TOAN")
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Lọc theo vai trò (người kiêm 2 vai trò hiện 1 lần), từ khóa không dấu, trạng thái")
    void shouldFilterUsers() throws Exception {
        create("lan.bh", "Nguyễn Thị Lan", "matkhau123", "SALES_STAFF", "WAREHOUSE_MANAGER");
        create("hung.kho", "Trần Văn Hùng", "matkhau123", "WAREHOUSE_MANAGER");

        mockMvc.perform(get(BASE).param("role", "WAREHOUSE_MANAGER"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2));
        mockMvc.perform(get(BASE).param("keyword", "hung"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("hung.kho"));
        mockMvc.perform(get(BASE).param("role", "SALES_STAFF").param("active", "true"))
                .andExpect(jsonPath("$.data.items[0].roles.length()").value(2));
    }

    @Test
    @DisplayName("BR-AUTH-06: đổi vai trò / khóa tài khoản → thu hồi mọi phiên; chỉ đổi họ tên thì không")
    void shouldRevokeSessionsOnRoleChangeOrLock() throws Exception {
        long id = id(create("lan.bh", "Lan", "matkhau123", "SALES_STAFF"));
        authService.login(new LoginRequest("lan.bh", "matkhau123"), "test");

        update(id, "Lan đổi tên", true, "SALES_STAFF").andExpect(status().isOk());
        assertThat(activeSessions(id)).isEqualTo(1);

        update(id, "Lan", true, "SALES_STAFF", "WAREHOUSE_MANAGER").andExpect(status().isOk());
        assertThat(activeSessions(id)).isZero();

        authService.login(new LoginRequest("lan.bh", "matkhau123"), "test");
        update(id, "Lan", false, "SALES_STAFF", "WAREHOUSE_MANAGER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
        assertThat(activeSessions(id)).isZero();
    }

    @Test
    @DisplayName("BR-AUTH-05: không khóa / hạ quyền Quản lý hệ thống cuối cùng đang hoạt động")
    void shouldKeepAtLeastOneActiveAdmin() throws Exception {
        long other = id(create("ke.toan", "Kế toán", "matkhau123", "SALES_STAFF"));
        update(admin.getId(), "Quản trị", true, "SALES_STAFF")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("ít nhất một")));

        // Có thêm một quản trị viên khác → được phép hạ quyền người đầu
        update(other, "Kế toán", true, "SALES_STAFF", "SYSTEM_ADMIN").andExpect(status().isOk());
        update(admin.getId(), "Quản trị", true, "SALES_STAFF").andExpect(status().isOk());
    }

    @Test
    @DisplayName("BR-AUTH-05: không tự khóa tài khoản đang đăng nhập")
    void shouldNotLockSelf() throws Exception {
        SecurityTestSupport.createUser(userRepository, passwordEncoder, "admin2", true, Role.SYSTEM_ADMIN);
        mockMvc.perform(put(BASE + "/{id}", admin.getId())
                        .with(jwt().jwt(token -> token.subject(String.valueOf(admin.getId())))
                                .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Quản trị", false, "SYSTEM_ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(containsString("tự khóa")));
    }

    @Test
    @DisplayName("BR-AUTH-07: đặt lại mật khẩu → mật khẩu mới dùng được, mọi phiên cũ bị thu hồi")
    void shouldResetPassword() throws Exception {
        long id = id(create("lan.bh", "Lan", "matkhau123", "SALES_STAFF"));
        authService.login(new LoginRequest("lan.bh", "matkhau123"), "test");

        mockMvc.perform(post(BASE + "/{id}/reset-password", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"ngan\"}"))
                .andExpect(jsonPath("$.error.details[0].field").value("newPassword"));
        mockMvc.perform(post(BASE + "/{id}/reset-password", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"mat-khau-moi-1\"}"))
                .andExpect(status().isNoContent());

        assertThat(activeSessions(id)).isZero();
        authService.login(new LoginRequest("lan.bh", "mat-khau-moi-1"), "test");
    }

    @Test
    @DisplayName("Tài khoản không tồn tại → 404; không có DELETE → 405")
    void shouldHandleMissingUser() throws Exception {
        mockMvc.perform(get(BASE + "/{id}", 999_999)).andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/{id}", admin.getId()))
                .andExpect(status().isMethodNotAllowed());
    }

    // ===== Helpers =====

    private ResultActions create(String username, String fullName, String password, String... roles) throws Exception {
        Map<String, Object> body = Map.of("username", username, "fullName", fullName, "password", password,
                "roles", roles);
        return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions update(long id, String fullName, boolean active, String... roles) throws Exception {
        return mockMvc.perform(put(BASE + "/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(fullName, active, roles)));
    }

    private String updateBody(String fullName, boolean active, String... roles) throws Exception {
        return objectMapper.writeValueAsString(Map.of("fullName", fullName, "active", active, "roles", roles));
    }

    private long id(ResultActions result) throws Exception {
        JsonNode body = objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
        return body.at("/data/id").asLong();
    }

    private int activeSessions(long userId) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NULL", Integer.class, userId);
    }
}
