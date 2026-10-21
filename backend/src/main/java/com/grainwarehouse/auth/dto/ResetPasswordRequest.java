package com.grainwarehouse.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank(message = "Nhập mật khẩu mới") String newPassword) {
}
