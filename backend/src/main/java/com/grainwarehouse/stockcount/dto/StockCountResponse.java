package com.grainwarehouse.stockcount.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Chi tiết phiếu kiểm kê. movedSinceSnapshot (chỉ phiếu đang kiểm): id các SP trên phiếu có nhập/xuất sau lúc chụp
 * sổ sách — giao diện cảnh báo trước khi xác nhận (BR-CNT-03). Các mốc / người null → bỏ khỏi JSON.
 */
public record StockCountResponse(
        Long id,
        String code,
        StockDocumentStatus status,
        LocalDate documentDate,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        String note,
        Instant bookSnapshotAt,
        int lineCount,
        int countedLineCount,
        int differenceLineCount,
        long surplusQuantity,
        long shortageQuantity,
        String reason,
        String createdBy,
        String completedBy,
        Instant completedAt,
        Instant cancelledAt,
        Instant reversedAt,
        Instant createdAt,
        Instant updatedAt,
        List<Long> movedSinceSnapshot,
        List<StockCountLineResponse> lines) {

    public static StockCountResponse from(StockDocument document, List<Long> movedSinceSnapshot) {
        List<StockDocumentItem> items = document.getItems();
        return new StockCountResponse(
                document.getId(),
                document.getCode(),
                document.getStatus(),
                document.getDocumentDate(),
                document.getWarehouse().getId(),
                document.getWarehouse().getCode(),
                document.getWarehouse().getName(),
                document.getNote(),
                document.getBookSnapshotAt(),
                items.size(),
                (int) items.stream().filter(item -> item.getCountedQuantity() != null).count(),
                (int) items.stream().filter(item -> item.getDifference() != null && item.getDifference() != 0).count(),
                items.stream().filter(item -> item.getDifference() != null && item.getDifference() > 0)
                        .mapToLong(StockDocumentItem::getQuantity).sum(),
                items.stream().filter(item -> item.getDifference() != null && item.getDifference() < 0)
                        .mapToLong(StockDocumentItem::getQuantity).sum(),
                document.getReason(),
                document.getCreatedBy(),
                document.getCompletedBy(),
                document.getCompletedAt(),
                document.getCancelledAt(),
                document.getReversedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                movedSinceSnapshot,
                items.stream().map(StockCountLineResponse::from).toList());
    }
}
