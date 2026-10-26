package com.grainwarehouse.cashbook.dto;

import com.grainwarehouse.cashbook.entity.CashAccount;
import com.grainwarehouse.cashbook.entity.CashDirection;
import com.grainwarehouse.cashbook.entity.CashEntrySource;
import com.grainwarehouse.cashbook.repository.CashLedgerRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một dòng sổ quỹ. amount có dấu (+ thu, − chi); direction tách sẵn để hiển thị. reference = mã chứng từ ("PX000012",
 * "PN000003", "Lương 10/2026", "Tạm ứng NV001", "PC000004"). reversal = dòng hoàn; reversed = khoản gốc đã bị hoàn.
 * balance = số dư lũy kế sau dòng này (vắng mặt khi lọc theo nguồn).
 */
public record CashEntryResponse(
        CashEntrySource source,
        long sourceId,
        CashAccount account,
        LocalDate entryDate,
        BigDecimal amount,
        CashDirection direction,
        String reference,
        String counterparty,
        String category,
        String note,
        boolean reversal,
        boolean reversed,
        Instant createdAt,
        BigDecimal balance) {

    public static CashEntryResponse from(CashLedgerRepository.Row row, BigDecimal openingBalance) {
        return new CashEntryResponse(
                row.source(),
                row.sourceId(),
                row.account(),
                row.entryDate(),
                row.amount(),
                row.amount().signum() >= 0 ? CashDirection.IN : CashDirection.OUT,
                row.reference(),
                row.counterparty(),
                row.category(),
                row.note(),
                row.reversal(),
                row.reversed(),
                row.createdAt(),
                row.cumulative() == null ? null : openingBalance.add(row.cumulative()));
    }
}
