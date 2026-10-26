package com.grainwarehouse.cashbook.dto;

import com.grainwarehouse.cashbook.entity.CashAccount;
import com.grainwarehouse.cashbook.entity.CashEntrySource;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;

/**
 * Sổ quỹ một tài khoản trong [from, to] (BR-CASH-02): số dư đầu khoảng, tổng thu, tổng chi, số dư cuối khoảng và từng
 * dòng kèm số dư lũy kế. from không sớm hơn ngày đầu kỳ (bị kéo về ngày đầu kỳ nếu gửi sớm hơn).
 * Lọc theo nguồn (source): tổng thu/chi chỉ tính nguồn đó; số dư lũy kế từng dòng vắng mặt (mất nghĩa khi lọc);
 * số dư đầu / cuối khoảng vẫn là của cả tài khoản.
 */
public record CashLedgerResponse(
        CashAccount account,
        LocalDate startDate,
        LocalDate from,
        LocalDate to,
        CashEntrySource source,
        BigDecimal openingBalance,
        BigDecimal totalIn,
        BigDecimal totalOut,
        BigDecimal closingBalance,
        boolean negative,
        List<CashEntryResponse> entries) {
}
