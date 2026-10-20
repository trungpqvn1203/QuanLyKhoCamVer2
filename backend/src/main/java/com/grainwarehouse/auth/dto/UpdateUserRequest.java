package com.grainwarehouse.auth.dto;

import com.grainwarehouse.auth.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Tên đăng nhập không đổi được; mật khẩu đổi qua reset-password. */
public record UpdateUserRequest(
        @NotBlank(message = "Nhập họ tên") @Size(max = 100, message = "Họ tên tối đa 100 ký tự") String fullName,
        @NotEmpty(message = "Chọn ít nhất một vai trò") Set<Role> roles,
        @NotNull(message = "Thiếu trạng thái hoạt động") Boolean active) {
}
