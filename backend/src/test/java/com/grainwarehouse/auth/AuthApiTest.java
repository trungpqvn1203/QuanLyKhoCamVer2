package com.grainwarehouse.auth;

import com.grainwarehouse.auth.dto.UpdateUserRequest;
import com.grainwarehouse.auth.entity.AppUser;
import com.grainwarehouse.auth.entity.Role;
import com.grainwarehouse.auth.repository.AppUserRepository;
import com.grainwarehouse.auth.service.UserService;
import com.grainwarehouse.support.MutableClock;
import com.grainwarehouse.support.SecurityTestSupport;
import com.grainwarehouse.support.SecurityTestSupport.Session;
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
import org.springframework.mock.web.MockCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đăng nhập, cookie, CSRF, gia hạn (rotation + phát hiện dùng lại), hết hạn, đăng xuất, thu hồi — ADR-0003.
 * Dùng MockMvc "trần" (không người dùng mặc định) và đồng hồ chỉnh tay để kiểm hết hạn mà không chờ thật.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthApiTest {

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
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private AppUser sales;

    @BeforeEach
    void setUp() {
        clock.set(Instant.now());
        mockMvc = SecurityTestSupport.rawMockMvc(context);
        sales = SecurityTestSupport.createUser(userRepository, passwordEncoder, "lan.bh", true, Role.SALES_STAFF);
    }

    // ===== Đăng nhập & cookie =====

    @Test
    @DisplayName("Đăng nhập: 2 cookie HttpOnly, Secure, SameSite=Strict, đúng Path; token không nằm trong body, DB chỉ giữ hash")
    void shouldSetHardenedCookiesOnLogin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("  Lan.BH ", SecurityTestSupport.PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("lan.bh"))
                .andExpect(jsonPath("$.data.roles[0]").value("SALES_STAFF"))
                .andReturn();

        MockCookie access = (MockCookie) result.getResponse().getCookie("GW_AT");
        MockCookie refresh = (MockCookie) result.getResponse().getCookie("GW_RT");
        assertThat(access.isHttpOnly()).isTrue();
        assertThat(access.getSecure()).isTrue();
        assertThat(access.getSameSite()).isEqualTo("Strict");
        assertThat(access.getPath()).isEqualTo("/api");
        assertThat(access.getMaxAge()).isEqualTo(15 * 60);
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(refresh.getSecure()).isTrue();
        assertThat(refresh.getSameSite()).isEqualTo("Strict");
        assertThat(refresh.getPath()).isEqualTo("/api/auth");
        assertThat(refresh.getMaxAge()).isEqualTo(8 * 60 * 60);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(access.getValue()).doesNotContain(refresh.getValue());
        // DB chỉ giữ SHA-256 của refresh token, không giữ token gốc
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_token WHERE token_hash = ?",
                Integer.class, refresh.getValue())).isZero();
    }

    @Test
    @DisplayName("CSRF: đăng nhập không có token → 403 (luồng cookie + header thật: CsrfDoubleSubmitTest)")
    void shouldRequireCsrfOnLogin() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("lan.bh", SecurityTestSupport.PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value(containsString("CSRF")));
    }

    @Test
    @DisplayName("CSRF: đã đăng nhập nhưng request ghi thiếu CSRF → 403, không chạy service")
    void shouldRequireCsrfOnWrites() throws Exception {
        Session session = SecurityTestSupport.login(mockMvc, "lan.bh");
        mockMvc.perform(post("/api/customers").cookie(session.access())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.message").value(containsString("CSRF")));
    }

    @Test
    @DisplayName("Sai mật khẩu và tên không tồn tại → cùng một thông báo 401 (không lộ tên đăng nhập nào có thật)")
    void shouldNotRevealWhichUsernamesExist() throws Exception {
        String wrongPassword = login("lan.bh", "sai-mat-khau").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String unknownUser = login("khong.co", "sai-mat-khau").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(message(wrongPassword)).isEqualTo(message(unknownUser)).isEqualTo("Sai tên đăng nhập hoặc mật khẩu");
    }

    @Test
    @DisplayName("Tài khoản bị khóa: chỉ báo 'bị khóa' khi mật khẩu đúng; sai mật khẩu vẫn là thông báo chung")
    void shouldRevealLockOnlyAfterCorrectPassword() throws Exception {
        SecurityTestSupport.createUser(userRepository, passwordEncoder, "nghi.viec", false, Role.SALES_STAFF);
        login("nghi.viec", "sai-mat-khau").andExpect(jsonPath("$.error.message").value("Sai tên đăng nhập hoặc mật khẩu"));
        login("nghi.viec", SecurityTestSupport.PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value(containsString("bị khóa")));
    }

    @Test
    @DisplayName("Thiếu tên đăng nhập / mật khẩu → 400 VALIDATION_FAILED")
    void shouldValidateLoginBody() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    // ===== Access token =====

    @Test
    @DisplayName("Cookie access token xác thực API; không cookie → 401 envelope; token bị sửa / ký sai khóa → 401")
    void shouldAuthenticateWithAccessCookie() throws Exception {
        Session session = SecurityTestSupport.login(mockMvc, "lan.bh");
        mockMvc.perform(get("/api/products").cookie(session.access())).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").cookie(session.access()))
                .andExpect(jsonPath("$.data.id").value(sales.getId()))
                .andExpect(jsonPath("$.data.fullName").value("Người dùng lan.bh"));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // Đổi 1 ký tự phần chữ ký → chữ ký không khớp nội dung
        String token = session.access().getValue();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        mockMvc.perform(get("/api/products").cookie(new Cookie("GW_AT", tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token tự tạo với vai trò SYSTEM_ADMIN nhưng sai issuer → 401 (không giả được quyền)")
    void shouldRejectWrongIssuer() throws Exception {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("ke-gian").subject("1")
                .issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(600))
                .claim("roles", List.of("SYSTEM_ADMIN")).claim("username", "x").build();
        String forged = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        mockMvc.perform(get("/api/users").cookie(new Cookie("GW_AT", forged))).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Access token hết hạn sau 15 phút → 401; gia hạn vẫn chạy dù trình duyệt còn gửi kèm access token hết hạn")
    void shouldRefreshEvenWithExpiredAccessCookie() throws Exception {
        Session session = SecurityTestSupport.login(mockMvc, "lan.bh");
        clock.advance(Duration.ofMinutes(16));

        mockMvc.perform(get("/api/products").cookie(session.access())).andExpect(status().isUnauthorized());
        Session renewed = Session.from(refresh(session.access(), session.refresh())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("lan.bh"))
                .andReturn());
        mockMvc.perform(get("/api/products").cookie(renewed.access())).andExpect(status().isOk());
    }

    // ===== Gia hạn: rotation & dùng lại =====

    @Test
    @DisplayName("Rotation: mỗi lần gia hạn đổi refresh token; token cũ dùng lại trong 30 giây → 409 REFRESH_RACE, phiên vẫn còn")
    void shouldTreatQuickReuseAsRace() throws Exception {
        Session first = SecurityTestSupport.login(mockMvc, "lan.bh");
        Session second = Session.from(refresh(first.refresh()).andExpect(status().isOk()).andReturn());
        assertThat(second.refresh().getValue()).isNotEqualTo(first.refresh().getValue());

        clock.advance(Duration.ofSeconds(10));
        refresh(first.refresh())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REFRESH_RACE"))
                // Không xóa cookie: tab khác vừa nhận cookie mới
                .andExpect(result -> assertThat(result.getResponse().getCookie("GW_RT")).isNull());
        refresh(second.refresh()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Token cũ dùng lại sau 30 giây → nghi bị trộm: thu hồi CẢ family, token mới nhất cũng hết dùng được")
    void shouldRevokeFamilyOnLateReuse() throws Exception {
        Session first = SecurityTestSupport.login(mockMvc, "lan.bh");
        Session second = Session.from(refresh(first.refresh()).andReturn());

        clock.advance(Duration.ofSeconds(31));
        refresh(first.refresh())
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getCookie("GW_RT").getMaxAge()).isZero());
        refresh(second.refresh()).andExpect(status().isUnauthorized());

        entityManager.flush();
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT revoke_reason FROM refresh_token WHERE user_id = ?",
                String.class, sales.getId())).containsExactly("REUSE_DETECTED");
    }

    @Test
    @DisplayName("Không thao tác quá 8 giờ → gia hạn thất bại, xóa cookie")
    void shouldExpireAfterIdleTime() throws Exception {
        Session session = SecurityTestSupport.login(mockMvc, "lan.bh");
        clock.advance(Duration.ofHours(8).plusSeconds(1));
        refresh(session.refresh())
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getCookie("GW_AT").getMaxAge()).isZero());
    }

    @Test
    @DisplayName("Gia hạn liên tục vẫn hết hạn tuyệt đối sau 24 giờ từ lúc đăng nhập")
    void shouldExpireAtAbsoluteLifetime() throws Exception {
        Session session = SecurityTestSupport.login(mockMvc, "lan.bh");
        for (int i = 0; i < 3; i++) {
            clock.advance(Duration.ofHours(7));
            session = Session.from(refresh(session.refresh()).andExpect(status().isOk()).andReturn());
        }
        // Lần gia hạn lúc 21 giờ: hạn mới bị cắt ở mốc 24 giờ thay vì 21 + 8 = 29 giờ
        assertThat(session.refresh().getMaxAge()).isEqualTo(3 * 60 * 60);
        clock.advance(Duration.ofHours(3));
        refresh(session.refresh()).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh token lạ / không có cookie → 401")
    void shouldRejectUnknownRefreshToken() throws Exception {
        refresh(new Cookie("GW_RT", "khong-ton-tai")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").with(csrf())).andExpect(status().isUnauthorized());
    }

    // ===== Đăng xuất & thu hồi =====

    @Test
    @DisplayName("Đăng xuất chỉ thu hồi phiên của thiết bị đó; thiết bị khác vẫn gia hạn được")
    void shouldLogoutOnlyThisDevice() throws Exception {
        Session laptop = SecurityTestSupport.login(mockMvc, "lan.bh");
        Session phone = SecurityTestSupport.login(mockMvc, "lan.bh");

        mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(laptop.refresh()))
                .andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getCookie("GW_RT").getMaxAge()).isZero());
        refresh(laptop.refresh()).andExpect(status().isUnauthorized());
        refresh(phone.refresh()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Đổi mật khẩu: sai mật khẩu cũ → 400; đổi xong các thiết bị khác phải đăng nhập lại, thiết bị đang dùng giữ phiên")
    void shouldChangePasswordAndRevokeOtherSessions() throws Exception {
        Session current = SecurityTestSupport.login(mockMvc, "lan.bh");
        Session other = SecurityTestSupport.login(mockMvc, "lan.bh");

        changePassword(current, "sai-mat-khau", "mat-khau-moi-456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("currentPassword"));
        changePassword(current, SecurityTestSupport.PASSWORD, "ngan")
                .andExpect(jsonPath("$.error.details[0].field").value("newPassword"));
        changePassword(current, SecurityTestSupport.PASSWORD, "mat-khau-moi-456").andExpect(status().isNoContent());

        refresh(other.refresh()).andExpect(status().isUnauthorized());
        refresh(current.refresh()).andExpect(status().isOk());
        login("lan.bh", SecurityTestSupport.PASSWORD).andExpect(status().isUnauthorized());
        login("lan.bh", "mat-khau-moi-456").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Khóa tài khoản: không gia hạn được nữa; access token đã phát còn dùng tới khi hết 15 phút (giới hạn đã chấp nhận)")
    void shouldStopRefreshAfterAccountLocked() throws Exception {
        AppUser admin = SecurityTestSupport.createUser(userRepository, passwordEncoder, "quan.tri", true, Role.SYSTEM_ADMIN);
        Session session = SecurityTestSupport.login(mockMvc, "lan.bh");

        userService.update(sales.getId(), new UpdateUserRequest("Lan", Set.of(Role.SALES_STAFF), false), admin.getId());

        refresh(session.refresh()).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products").cookie(session.access())).andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(16));
        mockMvc.perform(get("/api/products").cookie(session.access())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Đổi vai trò: access token mới sau khi đăng nhập lại mang vai trò mới")
    void shouldCarryNewRolesAfterRelogin() throws Exception {
        AppUser admin = SecurityTestSupport.createUser(userRepository, passwordEncoder, "quan.tri", true, Role.SYSTEM_ADMIN);
        Session before = SecurityTestSupport.login(mockMvc, "lan.bh");
        mockMvc.perform(get("/api/suppliers").cookie(before.access())).andExpect(status().isForbidden());

        userService.update(sales.getId(),
                new UpdateUserRequest("Lan", Set.of(Role.SALES_STAFF, Role.WAREHOUSE_MANAGER), true), admin.getId());
        refresh(before.refresh()).andExpect(status().isUnauthorized());

        Session after = SecurityTestSupport.login(mockMvc, "lan.bh");
        mockMvc.perform(get("/api/suppliers").cookie(after.access())).andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").cookie(after.access()))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(jsonPath("$.data.roles.length()").value(2));
    }

    // ===== Helpers =====

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(credentials(username, password)));
    }

    private ResultActions refresh(Cookie... cookies) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(cookies));
    }

    private ResultActions changePassword(Session session, String current, String next) throws Exception {
        return mockMvc.perform(put("/api/auth/me/password").with(csrf())
                .cookie(session.access(), session.refresh())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"" + current + "\", \"newPassword\": \"" + next + "\"}"));
    }

    private static String credentials(String username, String password) {
        return "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}";
    }

    private static String message(String body) {
        int start = body.indexOf("\"message\":\"") + 11;
        return body.substring(start, body.indexOf('"', start));
    }
}
