package com.grainwarehouse.sales.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.sales.entity.OrderChannel;
import com.grainwarehouse.warehouse.entity.Warehouse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một dòng trong danh sách phiếu bán — không kèm dòng hàng và thanh toán (xem qua GET /{id}).
 * Phiếu Chờ xác nhận chưa có kho → các trường kho bị bỏ khỏi JSON.
 */
public record SalesOrderSummaryResponse(
        Long id,
        String code,
        StockDocumentStatus status,
        OrderChannel orderChannel,
        LocalDate documentDate,
        LocalDate requestedDeliveryDate,
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
        Warehouse warehouse = document.getWarehouse();
        return new SalesOrderSummaryResponse(
                document.getId(),
                document.getCode(),
                document.getStatus(),
                document.getOrderChannel(),
                document.getDocumentDate(),
                document.getRequestedDeliveryDate(),
                warehouse == null ? null : warehouse.getId(),
                warehouse == null ? null : warehouse.getCode(),
                warehouse == null ? null : warehouse.getName(),
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
