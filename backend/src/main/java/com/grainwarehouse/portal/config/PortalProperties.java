package com.grainwarehouse.portal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Cấu hình đăng nhập app khách (prefix grain.portal) — ADR-0005. Khóa ký, cookieSecure và thời gian ân hạn
 * gia hạn dùng chung AuthProperties; ở đây chỉ những gì khác nhân viên.
 *
 * @param issuer             `iss` của token khách — KHÁC nhân viên để decoder mỗi bên từ chối token của bên kia
 * @param refreshIdleTtl     BR-POR-05: khách mở app vài ngày một lần → 30 ngày (nhân viên 8 giờ)
 * @param refreshAbsoluteTtl BR-POR-05: tối đa 90 ngày kể từ lúc đăng nhập
 * @param maxFailedLogins    BR-POR-04: sai liên tiếp bao nhiêu lần thì khóa tạm
 * @param lockDuration       BR-POR-04: khóa tạm bao lâu
 */
@ConfigurationProperties(prefix = "grain.portal")
public record PortalProperties(
        @DefaultValue("grain-warehouse-portal") String issuer,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshIdleTtl,
        @DefaultValue("90d") Duration refreshAbsoluteTtl,
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockDuration) {
}
