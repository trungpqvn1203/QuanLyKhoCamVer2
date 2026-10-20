package com.grainwarehouse.auth.repository;

import com.grainwarehouse.auth.dto.UserSearchCriteria;
import com.grainwarehouse.auth.entity.AppUser;
import com.grainwarehouse.auth.entity.Role;
import com.grainwarehouse.common.search.CommonSpecifications;
import org.springframework.data.jpa.domain.Specification;

/** Ghép tiêu chí lọc tài khoản thành điều kiện SQL. */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<AppUser> from(UserSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.keywordContains(criteria, "username", "fullName"),
                hasRole(criteria.role()),
                CommonSpecifications.hasActive(criteria));
    }

    // Vai trò nằm ở bảng con app_user_role → JOIN; distinct để người kiêm 2 vai trò không hiện 2 lần
    private static Specification<AppUser> hasRole(Role role) {
        if (role == null) {
            return null;
        }
        return (root, query, cb) -> {
            query.distinct(true);
            return cb.equal(root.join("roles"), role);
        };
    }
}
