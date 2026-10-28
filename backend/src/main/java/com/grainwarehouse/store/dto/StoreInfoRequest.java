package com.grainwarehouse.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT = ghi đè toàn bộ; trường tùy chọn để trống → lưu NULL (không in dòng đó). */
public record StoreInfoRequest(
        @NotBlank(message = "Tên cửa hàng không được để trống")
        @Size(max = 200, message = "Tên cửa hàng tối đa 200 ký tự")
        String name,

        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address,

        @Size(max = 50, message = "Số điện thoại tối đa 50 ký tự")
        String phone,

        @Size(max = 300, message = "Lời chào cuối phiếu tối đa 300 ký tự")
        String receiptFooter) {
}
