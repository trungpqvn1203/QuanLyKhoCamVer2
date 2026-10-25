package com.grainwarehouse.payroll.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Một lần trả lương cho một dòng lương (BR-EPAY-02): tiền mặt, chuyển khoản và/hoặc số TRỪ vào tạm ứng — mỗi phần > 0
 * thành một dòng payroll_payment riêng. Phải có ít nhất một phần > 0 (kiểm ở service, lỗi gắn vào cashAmount).
 * Tiền tính đến đồng — không có phần lẻ.
 */
public record PayrollPaymentRequest(
        // Không gửi = hôm nay (giờ VN); chọn ngày nào cũng được
        LocalDate paymentDate,

        @PositiveOrZero(message = "Tiền mặt không được âm")
        @Digits(integer = 18, fraction = 0, message = "Tiền mặt phải là số đồng nguyên")
        BigDecimal cashAmount,

        @PositiveOrZero(message = "Tiền chuyển khoản không được âm")
        @Digits(integer = 18, fraction = 0, message = "Tiền chuyển khoản phải là số đồng nguyên")
        BigDecimal transferAmount,

        @PositiveOrZero(message = "Số trừ tạm ứng không được âm")
        @Digits(integer = 18, fraction = 0, message = "Số trừ tạm ứng phải là số đồng nguyên")
        BigDecimal advanceDeduction,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note) {
}
