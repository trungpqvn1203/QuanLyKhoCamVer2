package com.grainwarehouse.opening.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Chi tiết phiếu tồn đầu kỳ kèm dòng hàng. Các mốc completedAt/cancelledAt/reversedAt/reason/completedBy
 * null → bỏ khỏi JSON. totalAmount = tổng giá trị tham khảo (đơn giá tùy chọn, BR-OPN-10).
 */
public record OpeningStockResponse(
        Long id,
        String code,
        StockDocumentStatus status,
        LocalDate documentDate,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        String note,
        int lineCount,
        long totalQuantity,
        BigDecimal totalAmount,
        String reason,
        String createdBy,
        String completedBy,
        Instant completedAt,
        Instant cancelledAt,
        Instant reversedAt,
        Instant createdAt,
        Instant updatedAt,
        List<OpeningStockLineResponse> lines) {

    public static OpeningStockResponse from(StockDocument document) {
        List<StockDocumentItem> items = document.getItems();
        return new OpeningStockResponse(
                document.getId(),
                document.getCode(),
                document.getStatus(),
                document.getDocumentDate(),
                document.getWarehouse().getId(),
                document.getWarehouse().getCode(),
                document.getWarehouse().getName(),
                document.getNote(),
                items.size(),
                items.stream().mapToLong(StockDocumentItem::getQuantity).sum(),
                document.getTotalAmount(),
                document.getReason(),
                document.getCreatedBy(),
                document.getCompletedBy(),
                document.getCompletedAt(),
                document.getCancelledAt(),
                document.getReversedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                items.stream().map(OpeningStockLineResponse::from).toList());
    }
}
