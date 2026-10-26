package com.grainwarehouse.cashbook.dto;

import com.grainwarehouse.cashbook.entity.CashAccount;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Số dư hiện tại của quỹ tiền mặt và tài khoản (BR-CASH-06). configured = false: chưa đặt đầu kỳ — chỉ có configured
 * và today. Số dư "hôm nay" = đầu kỳ + mọi khoản có ngày từ đầu kỳ tới hôm nay (giờ VN); khoản ghi ngày tương lai
 * chưa tính. negative = số dư âm → giao diện cảnh báo đỏ (không chặn khoản chi nào).
 */
public record CashBookSummaryResponse(
        boolean configured,
        LocalDate today,
        LocalDate startDate,
        AccountSummary cash,
        AccountSummary bank) {

    public record AccountSummary(
            CashAccount account,
            BigDecimal balance,
            BigDecimal todayIn,
            BigDecimal todayOut,
            boolean negative) {
    }
}
