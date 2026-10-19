package com.grainwarehouse.customer.dto;

import com.grainwarehouse.customer.entity.CustomerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Không có trường code: mã do hệ thống tự sinh (BR-CUS-02).
 * Định dạng SĐT kiểm ở service sau khi chuẩn hóa (BR-CUS-06) — @Pattern ở đây sẽ từ chối "0901 234 567".
 */
public record CreateCustomerRequest(
        @NotBlank(message = "Tên khách hàng không được để trống")
        @Size(max = 200, message = "Tên khách hàng tối đa 200 ký tự")
        String name,

        @NotNull(message = "Loại khách hàng không được để trống")
        CustomerType type,

        @NotBlank(message = "Số điện thoại không được để trống")
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        @Size(max = 200, message = "Người liên hệ tối đa 200 ký tự")
        String contactName,

        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address,

        @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
        String note) {
}
