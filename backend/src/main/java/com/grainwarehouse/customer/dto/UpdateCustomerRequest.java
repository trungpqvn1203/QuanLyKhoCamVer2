package com.grainwarehouse.customer.dto;

import com.grainwarehouse.customer.entity.CustomerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT = cập nhật toàn bộ. Không có code (không sửa được); có active để mở lại khách (BR-CUS-05). */
public record UpdateCustomerRequest(
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
        String note,

        @NotNull(message = "Trạng thái không được để trống")
        Boolean active) {
}
