package com.grainwarehouse.portal.dto;

import com.grainwarehouse.portal.entity.CustomerAccount;

import java.time.Instant;

/**
 * Tài khoản app của một khách, nhìn từ phía cửa hàng (BR-POR-02, 10). exists = false → khách chưa có tài khoản,
 * các trường còn lại bị bỏ khỏi JSON. lockedUntil chỉ có khi đang khóa tạm do sai mật khẩu (BR-POR-04).
 */
public record PortalAccountResponse(
        Long customerId,
        boolean exists,
        Boolean active,
        Boolean mustChangePassword,
        Instant lockedUntil,
        Instant lastLoginAt,
        String createdBy,
        Instant createdAt) {

    public static PortalAccountResponse none(Long customerId) {
        return new PortalAccountResponse(customerId, false, null, null, null, null, null, null);
    }

    public static PortalAccountResponse from(CustomerAccount account, Instant now) {
        return new PortalAccountResponse(account.getCustomer().getId(), true, account.isActive(),
                account.isMustChangePassword(), account.isLocked(now) ? account.getLockedUntil() : null,
                account.getLastLoginAt(), account.getCreatedBy(), account.getCreatedAt());
    }
}
