package com.grainwarehouse.support;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * CHỈ có trong test. Mọi request của MockMvc tự cấu hình (@AutoConfigureMockMvc) mặc định chạy với tư cách
 * SYSTEM_ADMIN và có CSRF token hợp lệ → test nghiệp vụ (nhập, bán, tồn...) tập trung vào nghiệp vụ, không phải
 * lặp lại bước đăng nhập ở mỗi request.
 *
 * Chuỗi filter bảo mật VẪN chạy thật; chỉ bước "đọc JWT từ cookie" được thay bằng người dùng giả lập.
 * Test bảo mật (SecurityTestSupport) dựng MockMvc riêng KHÔNG có mặc định này: phải đăng nhập, gửi CSRF thật.
 */
@Configuration
public class DefaultTestUserConfig {

    /** Subject "0" không trùng tài khoản thật nào — endpoint đọc sub (đổi mật khẩu...) được test riêng. */
    public static final String DEFAULT_SUBJECT = "0";

    @Bean
    public MockMvcBuilderCustomizer defaultTestUser() {
        return builder -> builder.defaultRequest(get("/")
                .with(csrf())
                .with(jwt().jwt(token -> token.subject(DEFAULT_SUBJECT).claim("username", "test-admin"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))));
    }
}
