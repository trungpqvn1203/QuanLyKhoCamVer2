package com.grainwarehouse.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Không có trường code: mã do hệ thống tự sinh (BR-PRD-02). */
public record CreateProductRequest(
        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự")
        String name,

        @NotNull(message = "Danh mục không được để trống")
        Long categoryId,

        @NotNull(message = "Đơn vị tính không được để trống")
        Long unitId,

        @NotNull(message = "Khối lượng tịnh không được để trống")
        @DecimalMin(value = "0", inclusive = false, message = "Khối lượng tịnh phải lớn hơn 0")
        @Digits(integer = 7, fraction = 3, message = "Khối lượng tịnh tối đa 7 chữ số nguyên và 3 chữ số thập phân")
        BigDecimal netWeightKg,

        @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
        String description,

        // BR-STK-03: tùy chọn; không gửi = không cảnh báo Sắp hết
        @PositiveOrZero(message = "Tồn tối thiểu không được âm")
        Integer minStockQuantity) {
}
