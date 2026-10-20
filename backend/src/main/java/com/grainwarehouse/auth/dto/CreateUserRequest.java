package com.grainwarehouse.auth.dto;

import com.grainwarehouse.auth.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Định dạng tên đăng nhập và độ dài mật khẩu kiểm ở PasswordPolicy. */
public record CreateUserRequest(
        @NotBlank(message = "Nhập tên đăng nhập") String username,
        @NotBlank(message = "Nhập họ tên") @Size(max = 100, message = "Họ tên tối đa 100 ký tự") String fullName,
        @NotBlank(message = "Nhập mật khẩu") String password,
        @NotEmpty(message = "Chọn ít nhất một vai trò") Set<Role> roles) {
}
