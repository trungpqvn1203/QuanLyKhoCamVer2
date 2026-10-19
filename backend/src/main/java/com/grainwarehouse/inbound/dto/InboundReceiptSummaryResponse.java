package com.grainwarehouse.inbound.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Một dòng trong danh sách phiếu nhập — không kèm dòng hàng (xem chi tiết qua GET /{id}). */
public record InboundReceiptSummaryResponse(
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
        BigDecimal totalAmount,
        Instant completedAt,
        Instant createdAt) {

    public static InboundReceiptSummaryResponse from(StockDocument document) {
        return new InboundReceiptSummaryResponse(
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
                document.getTotalAmount(),
                document.getCompletedAt(),
                document.getCreatedAt());
    }
}
