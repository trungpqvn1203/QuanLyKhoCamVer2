package com.grainwarehouse.cashbook.dto;

import com.grainwarehouse.cashbook.entity.CashAccount;
import com.grainwarehouse.cashbook.entity.CashDirection;
import com.grainwarehouse.common.search.HasKeyword;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Tiêu chí lọc phiếu thu/chi (GET /api/cash-vouchers?keyword=&direction=&account=&categoryId=&voucherDateFrom=
 * &voucherDateTo=). keyword khớp mã phiếu hoặc người nộp/nhận. Mọi trường tùy chọn: null = không lọc.
 */
public record CashVoucherSearchCriteria(
        String keyword,
        CashDirection direction,
        CashAccount account,
        Long categoryId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate voucherDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate voucherDateTo) implements HasKeyword {
}
