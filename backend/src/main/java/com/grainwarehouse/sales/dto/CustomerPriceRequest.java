package com.grainwarehouse.sales.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Đặt giá bán riêng của một SP cho một khách (BR-SAL-06). */
public record CustomerPriceRequest(
        @NotNull(message = "Đơn giá không được để trống")
        @PositiveOrZero(message = "Đơn giá không được âm")
        @Digits(integer = 13, fraction = 2, message = "Đơn giá tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
        BigDecimal unitPrice) {
}
