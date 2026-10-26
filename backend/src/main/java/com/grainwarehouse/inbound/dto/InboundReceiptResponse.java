package com.grainwarehouse.inbound.dto;

import com.grainwarehouse.inbound.entity.SupplierPayment;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chi tiết phiếu nhập kèm dòng hàng và lịch sử trả tiền NCC. Các mốc completedAt/cancelledAt/reversedAt/reason
 * null → bỏ khỏi JSON. tracksDebt = false: phiếu cũ coi như đã trả đủ, không trả tiền được (BR-SPAY-05).
 */
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
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        boolean tracksDebt,
        String reason,
        Instant completedAt,
        Instant cancelledAt,
        Instant reversedAt,
        Instant createdAt,
        Instant updatedAt,
        List<InboundReceiptLineResponse> lines,
        List<SupplierPaymentResponse> payments) {

    public static InboundReceiptResponse from(StockDocument document, List<SupplierPayment> payments) {
        Set<Long> reversedIds = payments.stream()
                .filter(payment -> payment.getReversalOf() != null)
                .map(payment -> payment.getReversalOf().getId())
                .collect(Collectors.toSet());
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
                document.getPaidAmount(),
                document.getOutstandingAmount(),
                document.isTracksDebt(),
                document.getReason(),
                document.getCompletedAt(),
                document.getCancelledAt(),
                document.getReversedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getItems().stream().map(InboundReceiptLineResponse::from).toList(),
                payments.stream().map(payment -> SupplierPaymentResponse.from(payment, reversedIds)).toList());
    }
}
