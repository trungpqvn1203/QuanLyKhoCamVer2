package com.grainwarehouse.inbound.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Công nợ phải trả một NCC (BR-SPAY-01): tổng còn nợ trên các phiếu nhập Hoàn tất chưa trả đủ. */
public record SupplierDebtResponse(
        Long supplierId,
        String supplierCode,
        String supplierName,
        String supplierTaxCode,
        String supplierPhone,
        boolean supplierActive,
        BigDecimal outstandingAmount,
        long openReceiptCount,
        LocalDate oldestDocumentDate) {
}
