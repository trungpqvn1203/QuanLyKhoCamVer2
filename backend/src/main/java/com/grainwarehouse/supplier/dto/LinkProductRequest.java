package com.grainwarehouse.supplier.dto;

import jakarta.validation.constraints.NotNull;

public record LinkProductRequest(
        @NotNull(message = "Sản phẩm không được để trống")
        Long productId) {
}
