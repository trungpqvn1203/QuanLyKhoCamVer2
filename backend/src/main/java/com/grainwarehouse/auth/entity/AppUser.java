package com.grainwarehouse.auth.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tài khoản đăng nhập. Tên đăng nhập không đổi được sau khi tạo (đã nằm trong JWT, log...).
 * Mật khẩu chỉ lưu dạng băm — không có cách nào đọc lại mật khẩu gốc.
 */
@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends AuditableEntity {

    @Column(nullable = false, length = 50, updatable = false)
    private String username;

    @Setter
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Setter
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Setter
    @Column(nullable = false)
    private boolean active = true;

    @Setter
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    // EAGER: vai trò luôn cần khi đăng nhập / phát token; mỗi người chỉ vài dòng.
    // BatchSize: trang danh sách 20 người tải vai trò bằng 1 câu "WHERE user_id IN (...)" thay vì 20 câu (N+1).
    // Không JOIN FETCH collection khi phân trang: Hibernate phải tải hết rồi cắt trang trong bộ nhớ.
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 50)
    @CollectionTable(name = "app_user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private Set<Role> roles = new HashSet<>();

    public AppUser(String username) {
        this.username = username;
    }

    /** Chỉ đọc: đổi vai trò qua replaceRoles để service luôn biết lúc nào vai trò thay đổi. */
    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public void replaceRoles(Collection<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }
}
