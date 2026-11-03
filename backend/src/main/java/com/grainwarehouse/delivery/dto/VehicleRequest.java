package com.grainwarehouse.delivery.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Thêm / sửa xe (BR-VEH-01, 02). active chỉ dùng khi sửa (mở lại xe đã ngừng); thêm mới luôn đang dùng.
 * Không gửi active khi sửa = giữ nguyên.
 */
public record VehicleRequest(
        @NotBlank(message = "Biển số không được để trống")
        @Size(max = 20, message = "Biển số tối đa 20 ký tự")
        String plateNumber,

        @Size(max = 100, message = "Tên xe tối đa 100 ký tự")
        String name,

        @DecimalMin(value = "0.001", message = "Tải trọng phải lớn hơn 0")
        @Digits(integer = 3, fraction = 3, message = "Tải trọng tối đa 999,999 tấn, 3 chữ số lẻ")
        BigDecimal capacityTons,

        Boolean active) {
}
