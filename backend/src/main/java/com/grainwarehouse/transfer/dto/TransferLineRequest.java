package com.grainwarehouse.transfer.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Một dòng điều chuyển (BR-TRF-06): SP và số lượng nguyên > 0 (BR-INV-07). Không có giá — hàng chỉ đổi kho. */
public record TransferLineRequest(
        @NotNull(message = "Sản phẩm không được để trống")
        Long productId,

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        Integer quantity) {
}
