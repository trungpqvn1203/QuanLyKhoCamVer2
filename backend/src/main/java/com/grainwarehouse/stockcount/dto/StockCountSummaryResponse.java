package com.grainwarehouse.stockcount.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.repository.StockCountTotals;

import java.time.Instant;
import java.time.LocalDate;

/** Một dòng trong danh sách phiếu kiểm kê — không kèm dòng hàng, chỉ số tổng hợp (BR-CNT-09). */
public record StockCountSummaryResponse(
        Long id,
        String code,
        StockDocumentStatus status,
        LocalDate documentDate,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        long lineCount,
        long countedLineCount,
        long differenceLineCount,
        long surplusQuantity,
        long shortageQuantity,
        String createdBy,
        String completedBy,
        Instant completedAt,
        Instant createdAt) {

    /** totals null = phiếu không có dòng (kho chưa có tồn lúc lập phiếu, chưa thêm dòng nào). */
    public static StockCountSummaryResponse from(StockDocument document, StockCountTotals totals) {
        return new StockCountSummaryResponse(
                document.getId(),
                document.getCode(),
                document.getStatus(),
                document.getDocumentDate(),
                document.getWarehouse().getId(),
                document.getWarehouse().getCode(),
                document.getWarehouse().getName(),
                totals == null ? 0 : totals.getLineCount(),
                totals == null ? 0 : totals.getCountedLineCount(),
                totals == null ? 0 : totals.getDifferenceLineCount(),
                totals == null ? 0 : totals.getSurplusQuantity(),
                totals == null ? 0 : totals.getShortageQuantity(),
                document.getCreatedBy(),
                document.getCompletedBy(),
                document.getCompletedAt(),
                document.getCreatedAt());
    }
}
