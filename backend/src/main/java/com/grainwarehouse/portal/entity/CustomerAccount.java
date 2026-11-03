package com.grainwarehouse.portal.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import com.grainwarehouse.customer.entity.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Tài khoản app của một khách (ADR-0005, BR-POR-01..10). Đăng nhập bằng SĐT của khách — không có tên đăng nhập
 * riêng. Không setter cho trạng thái: mọi thay đổi đi qua các hàm nghiệp vụ dưới đây để không quên phần đi kèm
 * (ví dụ đặt lại mật khẩu thì phải bắt đổi mật khẩu và xóa khóa tạm).
 */
@Getter
@Entity
@Table(name = "customer_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerAccount extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_by", nullable = false, length = 50, updatable = false)
    private String createdBy;

    /** BR-POR-02, 03: mật khẩu do cửa hàng đặt → khách phải đổi trước khi đặt hàng. */
    public CustomerAccount(Customer customer, String passwordHash, String createdBy) {
        this.customer = customer;
        this.passwordHash = passwordHash;
        this.createdBy = createdBy;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** BR-POR-04: sai lần thứ N → khóa tạm và đếm lại từ đầu sau khi hết khóa. */
    public void recordFailedLogin(Instant now, int maxFailed, Duration lockDuration) {
        failedLoginCount++;
        if (failedLoginCount >= maxFailed) {
            lockedUntil = now.plus(lockDuration);
            failedLoginCount = 0;
        }
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    /** Khách tự đổi mật khẩu. */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        mustChangePassword = false;
    }

    /** BR-POR-10: cửa hàng đặt lại → khách phải đổi lại; bỏ khóa tạm để khách đăng nhập được ngay. */
    public void resetPassword(String passwordHash) {
        this.passwordHash = passwordHash;
        mustChangePassword = true;
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public void lock() {
        active = false;
    }

    public void unlock() {
        active = true;
        failedLoginCount = 0;
        lockedUntil = null;
    }
}
