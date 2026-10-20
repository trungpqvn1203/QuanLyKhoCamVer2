package com.grainwarehouse.auth.dto;

import com.grainwarehouse.auth.entity.Role;
import com.grainwarehouse.common.search.HasActive;
import com.grainwarehouse.common.search.HasKeyword;

/** GET /api/users?keyword=&role=&active= — keyword khớp tên đăng nhập hoặc họ tên. */
public record UserSearchCriteria(String keyword, Role role, Boolean active) implements HasKeyword, HasActive {
}
