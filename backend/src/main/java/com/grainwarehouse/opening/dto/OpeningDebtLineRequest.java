package com.grainwarehouse.opening.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Một khoản nợ đầu kỳ: một khách (hoặc NCC) và số nợ tổng (BR-ODB-02 — chưa chi tiết từng hóa đơn cũ). */
public record OpeningDebtLineRequest(
        @NotNull(message = "Chọn khách hàng / nhà cung cấp")
        Long partyId,

        @NotNull(message = "Nhập số nợ")
        @Positive(message = "Số nợ phải lớn hơn 0")
        @Digits(integer = 18, fraction = 0, message = "Số nợ phải là số đồng nguyên")
        BigDecimal amount,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note) {
}
