package com.grainwarehouse.auth;

import com.grainwarehouse.auth.entity.Role;
import com.grainwarehouse.auth.repository.AppUserRepository;
import com.grainwarehouse.support.SecurityTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chống CSRF kiểu double-submit đúng như trình duyệt làm: đọc cookie XSRF-TOKEN rồi gửi lại trong header X-XSRF-TOKEN.
 *
 * Phải chạy trong Spring context RIÊNG (property bên dưới chỉ để tách context) và KHÔNG dùng postprocessor csrf():
 * csrf() của spring-security-test thay vĩnh viễn CsrfTokenRepository trong CsrfFilter — filter dùng chung cho cả
 * context — bằng bản giả lưu token trong session. Sau đó cookie XSRF-TOKEN thật không còn được tạo.
 */
@SpringBootTest(properties = "grain.test.context=csrf-double-submit")
@ActiveProfiles("test")
@Transactional
class CsrfDoubleSubmitTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = SecurityTestSupport.rawMockMvc(context);
        SecurityTestSupport.createUser(userRepository, passwordEncoder, "lan.bh", true, Role.SALES_STAFF);
    }

    @Test
    @DisplayName("GET /api/auth/csrf đặt cookie XSRF-TOKEN: JS đọc được (không HttpOnly), SameSite=Strict, Secure")
    void shouldIssueReadableCsrfCookie() throws Exception {
        Cookie xsrf = fetchCsrfCookie();
        // CookieCsrfTokenRepository dùng Cookie của Servlet 6: SameSite nằm trong attribute
        assertThat(xsrf.isHttpOnly()).isFalse();
        assertThat(xsrf.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(xsrf.getSecure()).isTrue();
        assertThat(xsrf.getPath()).isEqualTo("/");
    }

    @Test
    @DisplayName("Header khớp cookie → qua CSRF; header khác cookie hoặc thiếu header → 403")
    void shouldAcceptOnlyMatchingHeader() throws Exception {
        Cookie xsrf = fetchCsrfCookie();

        mockMvc.perform(login().cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())).andExpect(status().isOk());
        mockMvc.perform(login().cookie(xsrf).header("X-XSRF-TOKEN", "gia-mao")).andExpect(status().isForbidden());
        // Trang độc hại: trình duyệt tự gửi cookie nhưng trang đó không đọc được cookie để đặt header
        mockMvc.perform(login().cookie(xsrf)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Request ghi sau khi đăng nhập: có cookie đăng nhập nhưng thiếu header CSRF → 403")
    void shouldProtectWritesOfLoggedInUser() throws Exception {
        Cookie xsrf = fetchCsrfCookie();
        MvcResult loggedIn = mockMvc.perform(login().cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())).andReturn();
        Cookie access = loggedIn.getResponse().getCookie("GW_AT");

        mockMvc.perform(post("/api/customers").cookie(access, xsrf)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // Có header khớp → qua CSRF, tới validation của controller
        mockMvc.perform(post("/api/customers").cookie(access, xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private Cookie fetchCsrfCookie() throws Exception {
        Cookie xsrf = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        return xsrf;
    }

    private static MockHttpServletRequestBuilder login() {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"lan.bh\", \"password\": \"" + SecurityTestSupport.PASSWORD + "\"}");
    }
}
