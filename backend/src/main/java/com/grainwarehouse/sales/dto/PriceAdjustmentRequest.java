package com.grainwarehouse.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Điều chỉnh giá phiếu bán đã giao, chưa thu tiền (BR-SAL-23, 24). Chỉ gửi các dòng cần đổi, theo productId
 * (mỗi SP một dòng trên phiếu). Số lượng không đổi được ở đây.
 */
public record PriceAdjustmentRequest(
        @NotBlank(message = "Phải ghi lý do điều chỉnh giá")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason,

        @NotEmpty(message = "Phải có ít nhất một dòng đổi giá")
        @Size(max = 200, message = "Tối đa 200 dòng")
        @Valid
        List<Line> lines) {

    public record Line(
            @NotNull(message = "Sản phẩm không được để trống")
            Long productId,

            @NotNull(message = "Đơn giá không được để trống")
            @PositiveOrZero(message = "Đơn giá không được âm")
            @Digits(integer = 13, fraction = 2, message = "Đơn giá tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
            BigDecimal unitPrice) {
    }
}
