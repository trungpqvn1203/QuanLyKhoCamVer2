package com.grainwarehouse.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Không có fixedNetWeightKg: người dùng không đặt được khối lượng cố định (BR-UNIT-03). */
public record CreateProductUnitRequest(
        @NotBlank(message = "Tên đơn vị không được để trống")
        @Size(max = 50, message = "Tên đơn vị tối đa 50 ký tự")
        String name) {
}
