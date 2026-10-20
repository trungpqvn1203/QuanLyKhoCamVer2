package com.grainwarehouse.auth.repository;

import com.grainwarehouse.auth.entity.AppUser;
import com.grainwarehouse.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    // username luôn lưu chữ thường (CHECK ở V14) → so khớp trực tiếp, dùng được unique index
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Số tài khoản đang hoạt động có vai trò này, trừ một người — để không khóa/hạ quyền quản trị viên cuối cùng. */
    @Query("""
            select count(u) from AppUser u join u.roles r
            where r = :role and u.active = true and u.id <> :excludedId
            """)
    long countActiveWithRoleExcluding(@Param("role") Role role, @Param("excludedId") Long excludedId);
}
