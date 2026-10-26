package com.grainwarehouse.cashbook.dto;

import com.grainwarehouse.cashbook.entity.CashAccount;
import com.grainwarehouse.cashbook.entity.CashDirection;
import com.grainwarehouse.cashbook.entity.CashVoucher;
import com.grainwarehouse.cashbook.entity.CashVoucherType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một phiếu thu/chi khác. direction là chiều của phiếu GỐC (phiếu hoàn giữ nguyên chiều gốc, tác động ngược dấu).
 * reversalOfId / reversalOfCode chỉ có ở phiếu hoàn; reversed = phiếu gốc này đã bị hoàn.
 */
public record CashVoucherResponse(
        Long id,
        String code,
        CashVoucherType type,
        CashDirection direction,
        CashAccount account,
        Long categoryId,
        String categoryName,
        BigDecimal amount,
        LocalDate voucherDate,
        String counterparty,
        String note,
        Long reversalOfId,
        String reversalOfCode,
        boolean reversed,
        Instant createdAt) {

    public static CashVoucherResponse from(CashVoucher voucher, boolean reversed) {
        CashVoucher original = voucher.getReversalOf();
        return new CashVoucherResponse(
                voucher.getId(),
                voucher.getCode(),
                voucher.getType(),
                voucher.getDirection(),
                voucher.getAccount(),
                voucher.getCategory().getId(),
                voucher.getCategory().getName(),
                voucher.getAmount(),
                voucher.getVoucherDate(),
                voucher.getCounterparty(),
                voucher.getNote(),
                original == null ? null : original.getId(),
                original == null ? null : original.getCode(),
                reversed,
                voucher.getCreatedAt());
    }
}
