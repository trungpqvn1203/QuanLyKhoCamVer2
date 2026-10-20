package com.grainwarehouse.sales.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Một dòng hàng của phiếu bán. Số lượng nguyên (BR-INV-07), phải > 0 (BR-SAL-01). */
public record SalesOrderLineRequest(
        @NotNull(message = "Sản phẩm không được để trống")
        Long productId,

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        Integer quantity,

        // BR-SAL-07: giá thực bán — FE điền sẵn từ bảng giá của khách, người bán sửa được. 0 = hàng tặng
        @NotNull(message = "Đơn giá không được để trống")
        @PositiveOrZero(message = "Đơn giá không được âm")
        @Digits(integer = 13, fraction = 2, message = "Đơn giá tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
        BigDecimal unitPrice) {
}
