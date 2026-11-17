package com.grainwarehouse.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/** Đặt hoặc bỏ đơn giá bán chung của một SP (BR-PRD-07). salePrice = null nghĩa là bỏ giá. */
public record ProductSalePriceRequest(
        @DecimalMin(value = "0", message = "Đơn giá bán không được âm")
        @Digits(integer = 13, fraction = 2, message = "Đơn giá bán tối đa 13 chữ số nguyên và 2 chữ số thập phân")
        BigDecimal salePrice) {
}

