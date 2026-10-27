package com.grainwarehouse.opening.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.repository.DocumentLineTotals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Một dòng trong danh sách phiếu tồn đầu kỳ — không kèm dòng hàng, chỉ số dòng và tổng SL. */
public record OpeningStockSummaryResponse(
        Long id,
        String code,
        StockDocumentStatus status,
        LocalDate documentDate,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        long lineCount,
        long totalQuantity,
        BigDecimal totalAmount,
        String createdBy,
        String completedBy,
        Instant completedAt,
        Instant createdAt) {

    /** totals null = phiếu không còn dòng nào (không xảy ra vì phiếu luôn có ít nhất một dòng). */
    public static OpeningStockSummaryResponse from(StockDocument document, DocumentLineTotals totals) {
        return new OpeningStockSummaryResponse(
                document.getId(),
                document.getCode(),
                document.getStatus(),
                document.getDocumentDate(),
                document.getWarehouse().getId(),
                document.getWarehouse().getCode(),
                document.getWarehouse().getName(),
                totals == null ? 0 : totals.getLineCount(),
                totals == null ? 0 : totals.getQuantity(),
                document.getTotalAmount(),
                document.getCreatedBy(),
                document.getCompletedBy(),
                document.getCompletedAt(),
                document.getCreatedAt());
    }
}
