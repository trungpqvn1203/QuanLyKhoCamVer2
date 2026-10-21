package com.grainwarehouse.auth.dto;

import com.grainwarehouse.auth.entity.AppUser;
import com.grainwarehouse.auth.entity.Role;

import java.util.List;

/** Người đang đăng nhập — frontend dựa vào roles để hiện menu (chỉ để tiện; quyền thật kiểm ở backend). */
public record CurrentUserResponse(Long id, String username, String fullName, List<Role> roles) {

    public static CurrentUserResponse from(AppUser user) {
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getFullName(),
                user.getRoles().stream().sorted().toList());
    }
}
