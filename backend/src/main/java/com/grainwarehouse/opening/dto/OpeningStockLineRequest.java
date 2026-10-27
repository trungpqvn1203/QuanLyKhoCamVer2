package com.grainwarehouse.opening.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Một dòng tồn đầu kỳ (BR-OPN-02). Số lượng nguyên > 0 (BR-INV-07; 0 thì không cần dòng).
 * Đơn giá tùy chọn, chỉ để tham khảo vì chưa tính giá vốn (BR-OPN-10): không gửi = 0.
 */
public record OpeningStockLineRequest(
        @NotNull(message = "Sản phẩm không được để trống")
        Long productId,

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        Integer quantity,

        @PositiveOrZero(message = "Đơn giá không được âm")
        @Digits(integer = 13, fraction = 2, message = "Đơn giá tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
        BigDecimal unitPrice) {

    public BigDecimal effectiveUnitPrice() {
        return unitPrice == null ? BigDecimal.ZERO : unitPrice;
    }
}
