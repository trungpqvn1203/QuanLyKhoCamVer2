package com.grainwarehouse.sales.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.sales.entity.SalePayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Chi tiết phiếu bán kèm dòng hàng và lịch sử thanh toán. Trường null bị bỏ khỏi JSON. */
public record SalesOrderResponse(
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
        String customerPhone,
        String note,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        String reason,
        Instant completedAt,
        Instant cancelledAt,
        Instant reversedAt,
        Instant createdAt,
        Instant updatedAt,
        List<SalesOrderLineResponse> lines,
        List<SalePaymentResponse> payments) {

    public static SalesOrderResponse from(StockDocument document, List<SalePayment> payments) {
        Set<Long> reversedIds = payments.stream()
                .map(SalePayment::getReversalOf)
                .filter(Objects::nonNull)
                .map(SalePayment::getId)
                .collect(Collectors.toSet());
        return new SalesOrderResponse(
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
                document.getCustomer().getPhone(),
                document.getNote(),
                document.getTotalAmount(),
                document.getPaidAmount(),
                document.getOutstandingAmount(),
                document.getReason(),
                document.getCompletedAt(),
                document.getCancelledAt(),
                document.getReversedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getItems().stream().map(SalesOrderLineResponse::from).toList(),
                payments.stream().map(payment -> SalePaymentResponse.from(payment, reversedIds)).toList());
    }
}
