package com.grainwarehouse.cashbook.dto;

import com.grainwarehouse.cashbook.entity.CashBook;
import com.grainwarehouse.cashbook.entity.CashBookChange;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Đầu kỳ sổ quỹ hiện tại + lịch sử đặt/sửa (mới nhất trước). configured = false: chưa đặt đầu kỳ — các trường đầu kỳ
 * vắng mặt (NON_NULL), changes rỗng.
 */
public record CashBookOpeningResponse(
        boolean configured,
        LocalDate startDate,
        BigDecimal openingCash,
        BigDecimal openingBank,
        Instant updatedAt,
        List<Change> changes) {

    public static CashBookOpeningResponse of(CashBook book, List<CashBookChange> changes) {
        List<Change> history = changes.stream().map(Change::from).toList();
        if (book == null) {
            return new CashBookOpeningResponse(false, null, null, null, null, history);
        }
        return new CashBookOpeningResponse(true, book.getStartDate(), book.getOpeningCash(), book.getOpeningBank(),
                book.getUpdatedAt(), history);
    }

    /** old* vắng mặt = lần đặt đầu tiên. */
    public record Change(
            LocalDate oldStartDate,
            BigDecimal oldOpeningCash,
            BigDecimal oldOpeningBank,
            LocalDate newStartDate,
            BigDecimal newOpeningCash,
            BigDecimal newOpeningBank,
            String changedBy,
            Instant changedAt) {

        static Change from(CashBookChange change) {
            return new Change(change.getOldStartDate(), change.getOldOpeningCash(), change.getOldOpeningBank(),
                    change.getNewStartDate(), change.getNewOpeningCash(), change.getNewOpeningBank(),
                    change.getChangedBy(), change.getChangedAt());
        }
    }
}
