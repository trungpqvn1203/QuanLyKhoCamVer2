package com.grainwarehouse.cashbook.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Đặt / sửa đầu kỳ sổ quỹ (BR-CASH-03): ngày bắt đầu + số tiền mặt và số dư tài khoản thực tế tại ngày đó. */
public record CashBookOpeningRequest(
        @NotNull(message = "Chọn ngày bắt đầu sổ quỹ")
        LocalDate startDate,

        @NotNull(message = "Nhập số tiền mặt đầu kỳ (không có thì nhập 0)")
        @PositiveOrZero(message = "Tiền mặt đầu kỳ không được âm")
        @Digits(integer = 18, fraction = 0, message = "Tiền mặt đầu kỳ phải là số đồng nguyên")
        BigDecimal openingCash,

        @NotNull(message = "Nhập số dư tài khoản đầu kỳ (không có thì nhập 0)")
        @PositiveOrZero(message = "Số dư tài khoản đầu kỳ không được âm")
        @Digits(integer = 18, fraction = 0, message = "Số dư tài khoản đầu kỳ phải là số đồng nguyên")
        BigDecimal openingBank) {
}
