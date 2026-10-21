package com.grainwarehouse.auth.dto;

import com.grainwarehouse.auth.entity.AppUser;
import com.grainwarehouse.auth.entity.Role;

import java.time.Instant;
import java.util.List;

/** Tài khoản trong trang quản lý người dùng — không bao giờ kèm mật khẩu băm. */
public record UserResponse(
        Long id,
        String username,
        String fullName,
        List<Role> roles,
        boolean active,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getFullName(),
                user.getRoles().stream().sorted().toList(), user.isActive(), user.getLastLoginAt(),
                user.getCreatedAt(), user.getUpdatedAt());
    }
}
