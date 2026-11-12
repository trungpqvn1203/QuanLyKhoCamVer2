package com.grainwarehouse.transfer.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.repository.DocumentLineTotals;

import java.time.Instant;
import java.time.LocalDate;

/** Một dòng trong danh sách phiếu điều chuyển — không kèm dòng hàng, chỉ số dòng và tổng SL. */
public record TransferSummaryResponse(
        Long id,
        String code,
        StockDocumentStatus status,
        LocalDate documentDate,
        Long sourceWarehouseId,
        String sourceWarehouseCode,
        String sourceWarehouseName,
        Long targetWarehouseId,
        String targetWarehouseCode,
        String targetWarehouseName,
        long lineCount,
        long totalQuantity,
        String createdBy,
        String completedBy,
        Instant completedAt,
        Instant createdAt) {

    /** totals null = phiếu không còn dòng nào (không xảy ra vì phiếu luôn có ít nhất một dòng). */
    public static TransferSummaryResponse from(StockDocument document, DocumentLineTotals totals) {
        return new TransferSummaryResponse(
                document.getId(),
                document.getCode(),
                document.getStatus(),
                document.getDocumentDate(),
                document.getWarehouse().getId(),
                document.getWarehouse().getCode(),
                document.getWarehouse().getName(),
                document.getTargetWarehouse().getId(),
                document.getTargetWarehouse().getCode(),
                document.getTargetWarehouse().getName(),
                totals == null ? 0 : totals.getLineCount(),
                totals == null ? 0 : totals.getQuantity(),
                document.getCreatedBy(),
                document.getCompletedBy(),
                document.getCompletedAt(),
                document.getCreatedAt());
    }
}
