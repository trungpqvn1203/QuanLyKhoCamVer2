package com.grainwarehouse.sales.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Một dòng trong danh sách phiếu bán — không kèm dòng hàng và thanh toán (xem qua GET /{id}). */
public record SalesOrderSummaryResponse(
        Long id,
        String code,
        StockDocumentStatus status,
        LocalDate documentDate,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long customerId,
        String customerCode,
        String customerName,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        Instant completedAt,
        Instant createdAt) {

    public static SalesOrderSummaryResponse from(StockDocument document) {
        return new SalesOrderSummaryResponse(
                document.getId(),
                document.getCode(),
                document.getStatus(),
                document.getDocumentDate(),
                document.getWarehouse().getId(),
                document.getWarehouse().getCode(),
                document.getWarehouse().getName(),
                document.getCustomer().getId(),
                document.getCustomer().getCode(),
                document.getCustomer().getName(),
                document.getTotalAmount(),
                document.getPaidAmount(),
                document.getOutstandingAmount(),
                document.getCompletedAt(),
                document.getCreatedAt());
    }
}
