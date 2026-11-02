package com.grainwarehouse.sales.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Một dòng khách đặt: SP + SL nguyên theo đơn vị tính của SP (BR-SAL-17). Không có giá. */
public record SaleRequestLineRequest(
        @NotNull(message = "Sản phẩm không được để trống")
        Long productId,

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        Integer quantity) {
}
