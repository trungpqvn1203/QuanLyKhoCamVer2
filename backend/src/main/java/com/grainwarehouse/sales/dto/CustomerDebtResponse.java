package com.grainwarehouse.sales.dto;

import com.grainwarehouse.customer.entity.CustomerType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Công nợ của một khách (BR-SAL-10): tổng còn nợ trên các phiếu bán Hoàn tất chưa thu đủ. */
public record CustomerDebtResponse(
        Long customerId,
        String customerCode,
        String customerName,
        CustomerType customerType,
        String customerPhone,
        boolean customerActive,
        BigDecimal outstandingAmount,
        long openOrderCount,
        LocalDate oldestDocumentDate) {
}
