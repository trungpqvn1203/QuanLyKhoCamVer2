package com.grainwarehouse.payroll.dto;

import com.grainwarehouse.payroll.entity.PayrollPayment;
import com.grainwarehouse.payroll.entity.PayrollPaymentMethod;
import com.grainwarehouse.payroll.entity.PayrollPaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Một dòng lịch sử trả lương. reversalOfId chỉ có ở dòng hoàn; reversed = khoản trả này đã bị hoàn
 * (FE dùng để ẩn nút "Hoàn").
 */
public record PayrollPaymentResponse(
        Long id,
        PayrollPaymentType type,
        PayrollPaymentMethod method,
        BigDecimal amount,
        LocalDate paymentDate,
        String note,
        Long reversalOfId,
        boolean reversed,
        Instant createdAt) {

    public static PayrollPaymentResponse from(PayrollPayment payment, Set<Long> reversedIds) {
        return new PayrollPaymentResponse(
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
