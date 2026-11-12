package com.grainwarehouse.transfer.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Chi tiết phiếu điều chuyển kèm dòng hàng. Các mốc completedAt/cancelledAt/reversedAt/reason/completedBy
 * null → bỏ khỏi JSON. Không có tiền: điều chuyển không đổi giá trị hàng.
 */
public record TransferResponse(
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
        String note,
        int lineCount,
        long totalQuantity,
        String reason,
        String createdBy,
        String completedBy,
        Instant completedAt,
        Instant cancelledAt,
        Instant reversedAt,
        Instant createdAt,
        Instant updatedAt,
        List<TransferLineResponse> lines) {

    public static TransferResponse from(StockDocument document) {
        List<StockDocumentItem> items = document.getItems();
        return new TransferResponse(
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
                document.getNote(),
                items.size(),
                items.stream().mapToLong(StockDocumentItem::getQuantity).sum(),
                document.getReason(),
                document.getCreatedBy(),
                document.getCompletedBy(),
                document.getCompletedAt(),
                document.getCancelledAt(),
                document.getReversedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                items.stream().map(TransferLineResponse::from).toList());
    }
}
