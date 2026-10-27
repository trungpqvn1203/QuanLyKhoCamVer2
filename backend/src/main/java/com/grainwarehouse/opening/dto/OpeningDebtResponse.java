package com.grainwarehouse.opening.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Một khoản công nợ đầu kỳ. party* là khách hàng hoặc NCC tùy endpoint. payments cùng dạng với lịch sử thu tiền
 * phiếu bán / trả tiền phiếu nhập (SalePaymentResponse / SupplierPaymentResponse) — danh sách không kèm (null → bỏ).
 */
public record OpeningDebtResponse(
        Long id,
        String code,
        OpeningDebtParty party,
        StockDocumentStatus status,
        LocalDate documentDate,
        Long partyId,
        String partyCode,
        String partyName,
        boolean partyActive,
        String note,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        String reason,
        String createdBy,
        Instant completedAt,
        Instant reversedAt,
        Instant createdAt,
        Instant updatedAt,
        List<?> payments) {

    public static OpeningDebtResponse from(StockDocument document, OpeningDebtParty party, List<?> payments) {
        boolean customer = party == OpeningDebtParty.CUSTOMER;
        return new OpeningDebtResponse(
                document.getId(),
                document.getCode(),
                party,
                document.getStatus(),
                document.getDocumentDate(),
                customer ? document.getCustomer().getId() : document.getSupplier().getId(),
                customer ? document.getCustomer().getCode() : document.getSupplier().getCode(),
                customer ? document.getCustomer().getName() : document.getSupplier().getName(),
                customer ? document.getCustomer().isActive() : document.getSupplier().isActive(),
                document.getNote(),
                document.getTotalAmount(),
                document.getPaidAmount(),
                document.getOutstandingAmount(),
                document.getReason(),
                document.getCreatedBy(),
                document.getCompletedAt(),
                document.getReversedAt(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                payments);
    }
}
