package com.grainwarehouse.cashbook.dto;

import com.grainwarehouse.cashbook.entity.CashAccount;
import com.grainwarehouse.cashbook.entity.CashDirection;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lập phiếu thu (IN) hoặc phiếu chi (OUT) khác (BR-CASH-04). Mã phiếu do hệ thống sinh. */
public record CashVoucherRequest(
        @NotNull(message = "Chọn phiếu thu hay phiếu chi")
        CashDirection direction,

        @NotNull(message = "Chọn tiền mặt hay tài khoản")
        CashAccount account,

        @NotNull(message = "Chọn danh mục")
        Long categoryId,

        @NotNull(message = "Nhập số tiền")
        @Positive(message = "Số tiền phải lớn hơn 0")
        @Digits(integer = 18, fraction = 0, message = "Số tiền phải là số đồng nguyên")
        BigDecimal amount,

        // Không gửi = hôm nay (giờ VN); chọn tự do nhưng không trước ngày đầu kỳ sổ quỹ
        LocalDate voucherDate,

        @Size(max = 200, message = "Người nộp / nhận tối đa 200 ký tự")
        String counterparty,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note) {
}
