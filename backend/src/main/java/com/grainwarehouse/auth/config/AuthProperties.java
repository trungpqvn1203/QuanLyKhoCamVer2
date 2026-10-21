package com.grainwarehouse.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cấu hình xác thực (prefix grain.auth, xem application.yml). Thời hạn theo ADR-0003 mục 1.
 *
 * @param jwtSecret            khóa ký HS256, tối thiểu 32 byte — đọc từ biến môi trường JWT_SECRET, không commit
 * @param allowEphemeralSecret chỉ bật ở dev: thiếu JWT_SECRET thì sinh khóa ngẫu nhiên (khởi động lại = đăng nhập lại)
 * @param cookieSecure         cookie chỉ gửi qua HTTPS; trình duyệt cho phép ngoại lệ với http://localhost
 * @param refreshReuseGrace    token vừa bị thay trong khoảng này mà bị dùng lại → coi là 2 tab gia hạn cùng lúc,
 *                             không phải bị trộm
 */
@ConfigurationProperties(prefix = "grain.auth")
public record AuthProperties(
        String jwtSecret,
        @DefaultValue("false") boolean allowEphemeralSecret,
        @DefaultValue("grain-warehouse") String issuer,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("8h") Duration refreshIdleTtl,
        @DefaultValue("24h") Duration refreshAbsoluteTtl,
        @DefaultValue("30s") Duration refreshReuseGrace,
        @DefaultValue("true") boolean cookieSecure,
        String bootstrapAdminUsername,
        String bootstrapAdminPassword) {
}
