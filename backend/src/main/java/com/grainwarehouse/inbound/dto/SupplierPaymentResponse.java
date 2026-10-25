package com.grainwarehouse.inbound.dto;

import com.grainwarehouse.inbound.entity.SupplierPayment;
import com.grainwarehouse.inbound.entity.SupplierPaymentType;
import com.grainwarehouse.sales.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Một dòng lịch sử trả tiền NCC. reversalOfId chỉ có ở dòng hoàn; reversed = khoản trả này đã bị hoàn
 * (FE dùng để ẩn nút "Hoàn").
 */
public record SupplierPaymentResponse(
        Long id,
        SupplierPaymentType type,
        PaymentMethod method,
        BigDecimal amount,
        LocalDate paymentDate,
        String note,
        Long reversalOfId,
        boolean reversed,
        Instant createdAt) {

    public static SupplierPaymentResponse from(SupplierPayment payment, Set<Long> reversedIds) {
        return new SupplierPaymentResponse(
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
