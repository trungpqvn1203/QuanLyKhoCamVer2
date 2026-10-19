package com.grainwarehouse.employee.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * PUT = cập nhật toàn bộ: dailyWage/tonRate không gửi = bỏ cách tính lương đó.
 * Không có code (không sửa được); có active để mở lại nhân viên.
 */
public record UpdateEmployeeRequest(
        @NotBlank(message = "Tên nhân viên không được để trống")
        @Size(max = 200, message = "Tên nhân viên tối đa 200 ký tự")
        String name,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        @PositiveOrZero(message = "Lương ngày không được âm")
        @Digits(integer = 13, fraction = 2, message = "Lương ngày tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
        BigDecimal dailyWage,

        @PositiveOrZero(message = "Đơn giá tấn không được âm")
        @Digits(integer = 13, fraction = 2, message = "Đơn giá tấn tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
        BigDecimal tonRate,

        @NotNull(message = "Trạng thái không được để trống")
        Boolean active) {
}
