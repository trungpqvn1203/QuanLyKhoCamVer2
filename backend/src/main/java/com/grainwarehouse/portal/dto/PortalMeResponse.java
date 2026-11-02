package com.grainwarehouse.portal.dto;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.portal.entity.CustomerAccount;

/** Khách đang đăng nhập app. mustChangePassword = true → app mở màn hình đổi mật khẩu trước (BR-POR-03). */
public record PortalMeResponse(
        Long customerId,
        String customerCode,
        String customerName,
        String phone,
        boolean mustChangePassword) {

    public static PortalMeResponse from(CustomerAccount account) {
        Customer customer = account.getCustomer();
        return new PortalMeResponse(customer.getId(), customer.getCode(), customer.getName(), customer.getPhone(),
                account.isMustChangePassword());
    }
}
