package com.grainwarehouse.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Không có trường code: mã do hệ thống tự sinh (BR-SUP-02). phone/email/address tùy chọn (BR-SUP-01). */
public record CreateSupplierRequest(
        @NotBlank(message = "Tên nhà cung cấp không được để trống")
        @Size(max = 200, message = "Tên nhà cung cấp tối đa 200 ký tự")
        String name,

        @NotBlank(message = "Người liên hệ không được để trống")
        @Size(max = 200, message = "Người liên hệ tối đa 200 ký tự")
        String contactName,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        @Email(message = "Email không đúng định dạng")
        @Size(max = 254, message = "Email tối đa 254 ký tự")
        String email,

        @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
        String address) {
}
