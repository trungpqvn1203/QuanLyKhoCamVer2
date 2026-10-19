package com.grainwarehouse.inbound.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Chi tiết phiếu nhập kèm dòng hàng. Các mốc completedAt/cancelledAt/reversedAt/reason null → bỏ khỏi JSON. */
public record InboundReceiptResponse(
        Long id,
        String code,
        StockDocumentStatus status,
        LocalDate documentDate,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long supplierId,
        String supplierCode,
        String supplierName,
        String note,
        BigDecimal totalAmount,
        String reason,
        Instant completedAt,
        Instant cancelledAt,
        Instant reversedAt,
        Instant createdAt,
        Instant updatedAt,
        List<InboundReceiptLineResponse> lines) {

    public static InboundReceiptResponse from(StockDocument document) {
        return new InboundReceiptResponse(
                document.getId(),
                document.getCode(),
                document.getStatus(),
                document.getDocumentDate(),
                document.getWarehouse().getId(),
                document.getWarehouse().getCode(),
                document.getWarehouse().getName(),
                document.getSupplier().getId(),
                document.getSupplier().getCode(),
                document.getSupplier().getName(),
                document.getNote(),
                document.getTotalAmount(),
                document.getReason(),
                document.getCompletedAt(),
                document.getCancelledAt(),
                document.getReversedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getItems().stream().map(InboundReceiptLineResponse::from).toList());
    }
}
