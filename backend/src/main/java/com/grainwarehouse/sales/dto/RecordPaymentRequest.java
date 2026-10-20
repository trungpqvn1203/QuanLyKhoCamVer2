package com.grainwarehouse.sales.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Một lần thu tiền (BR-SAL-12): tiền mặt, chuyển khoản, hoặc cả hai. Phần nào > 0 thành một dòng
 * sale_payment riêng. Phải có ít nhất một phần > 0 (kiểm tra ở service, lỗi gắn vào cashAmount).
 * Tiền thu tính đến đồng — không có phần lẻ.
 */
public record RecordPaymentRequest(
        // Không gửi = hôm nay (giờ VN); không ở tương lai, không trước ngày phiếu
        LocalDate paymentDate,

        @PositiveOrZero(message = "Tiền mặt không được âm")
        @Digits(integer = 18, fraction = 0, message = "Tiền mặt phải là số đồng nguyên")
        BigDecimal cashAmount,

        @PositiveOrZero(message = "Tiền chuyển khoản không được âm")
        @Digits(integer = 18, fraction = 0, message = "Tiền chuyển khoản phải là số đồng nguyên")
        BigDecimal transferAmount,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note) {
}
