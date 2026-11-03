package com.grainwarehouse.portal.dto;

import jakarta.validation.constraints.NotBlank;

/** Cửa hàng đặt mật khẩu ban đầu / đặt lại (BR-POR-02, 10). Độ dài kiểm ở PasswordPolicy (BR-AUTH-03). */
public record PortalPasswordRequest(
        @NotBlank(message = "Nhập mật khẩu") String password) {
}
