package com.grainwarehouse.portal.dto;

import jakarta.validation.constraints.NotBlank;

/** BR-POR-01: đăng nhập app bằng SĐT của khách. */
public record PortalLoginRequest(
        @NotBlank(message = "Nhập số điện thoại") String phone,
        @NotBlank(message = "Nhập mật khẩu") String password) {
}
