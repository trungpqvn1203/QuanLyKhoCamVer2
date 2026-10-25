package com.grainwarehouse.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT = cập nhật toàn bộ. Không có code (không sửa được); có active để mở lại NCC. */
public record UpdateSupplierRequest(
        @NotBlank(message = "Tên nhà cung cấp không được để trống")
        @Size(max = 200, message = "Tên nhà cung cấp tối đa 200 ký tự")
        String name,

        // Định dạng kiểm ở service sau khi bỏ khoảng trắng (như SĐT khách — BR-CUS-06)
        @Size(max = 20, message = "Mã số thuế tối đa 20 ký tự")
        String taxCode,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        @Email(message = "Email không đúng định dạng")
        @Size(max = 254, message = "Email tối đa 254 ký tự")
        String email,

        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address,

        @NotNull(message = "Trạng thái không được để trống")
        Boolean active) {
}
