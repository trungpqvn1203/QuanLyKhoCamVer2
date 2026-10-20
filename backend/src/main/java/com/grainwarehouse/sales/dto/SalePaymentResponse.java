package com.grainwarehouse.sales.dto;

import com.grainwarehouse.sales.entity.PaymentMethod;
import com.grainwarehouse.sales.entity.SalePayment;
import com.grainwarehouse.sales.entity.SalePaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Một dòng lịch sử thanh toán. reversalOfId chỉ có ở dòng hoàn; reversed = khoản thu này đã bị hoàn
 * (FE dùng để ẩn nút "Hoàn").
 */
public record SalePaymentResponse(
        Long id,
        SalePaymentType type,
        PaymentMethod method,
        BigDecimal amount,
        LocalDate paymentDate,
        String note,
        Long reversalOfId,
        boolean reversed,
        Instant createdAt) {

    public static SalePaymentResponse from(SalePayment payment, Set<Long> reversedIds) {
        return new SalePaymentResponse(
                payment.getId(),
                payment.getType(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getPaymentDate(),
                payment.getNote(),
                payment.getReversalOf() == null ? null : payment.getReversalOf().getId(),
                reversedIds.contains(payment.getId()),
                payment.getCreatedAt());
    }
}
