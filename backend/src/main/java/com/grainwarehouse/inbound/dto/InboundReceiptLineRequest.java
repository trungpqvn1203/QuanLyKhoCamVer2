package com.grainwarehouse.inbound.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Một dòng hàng của phiếu nhập. Số lượng là Integer (BR-INV-07): gửi 2.5 → 400
 * (cấu hình accept-float-as-int: false trong application.yml).
 */
public record InboundReceiptLineRequest(
        @NotNull(message = "Sản phẩm không được để trống")
        Long productId,

        @NotNull(message = "Số lượng đặt không được để trống")
        @Positive(message = "Số lượng đặt phải lớn hơn 0")
        Integer orderedQuantity,

        // BR-IMP-03: không gửi = bằng SL đặt; 0 = NCC không giao dòng này (BR-IMP-A06)
        @PositiveOrZero(message = "Số lượng thực nhận không được âm")
        Integer receivedQuantity,

        // BR-MON-04, BR-IMP-A07: tối đa 2 chữ số thập phân, được bằng 0 (hàng tặng)
        @NotNull(message = "Đơn giá không được để trống")
        @PositiveOrZero(message = "Đơn giá không được âm")
        @Digits(integer = 13, fraction = 2, message = "Đơn giá tối đa 13 chữ số phần nguyên và 2 chữ số thập phân")
        BigDecimal unitPrice) {

    public int effectiveReceivedQuantity() {
        return receivedQuantity != null ? receivedQuantity : orderedQuantity;
    }
}
