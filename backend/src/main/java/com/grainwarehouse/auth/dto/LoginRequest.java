package com.grainwarehouse.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Nhập tên đăng nhập") String username,
        @NotBlank(message = "Nhập mật khẩu") String password) {
}
