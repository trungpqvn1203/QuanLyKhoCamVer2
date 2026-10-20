package com.grainwarehouse.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Độ dài mật khẩu mới kiểm ở PasswordPolicy (cùng chỗ với tạo / đặt lại mật khẩu). */
public record ChangePasswordRequest(
        @NotBlank(message = "Nhập mật khẩu hiện tại") String currentPassword,
        @NotBlank(message = "Nhập mật khẩu mới") String newPassword) {
}
