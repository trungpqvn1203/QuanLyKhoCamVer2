package com.grainwarehouse.employee.dto;

import com.grainwarehouse.employee.entity.EmployeeAdvance;
import com.grainwarehouse.employee.entity.EmployeeAdvanceType;
import com.grainwarehouse.sales.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Một dòng lịch sử tạm ứng. reversalOfId chỉ có ở dòng hoàn; reversed = khoản ứng này đã bị hoàn
 * (FE dùng để ẩn nút "Hoàn").
 */
public record EmployeeAdvanceResponse(
        Long id,
        EmployeeAdvanceType type,
        PaymentMethod method,
        BigDecimal amount,
        LocalDate advanceDate,
        String note,
        Long reversalOfId,
        boolean reversed,
        Instant createdAt) {

    public static EmployeeAdvanceResponse from(EmployeeAdvance advance, Set<Long> reversedIds) {
        return new EmployeeAdvanceResponse(
                advance.getId(),
                advance.getType(),
                advance.getMethod(),
                advance.getAmount(),
                advance.getAdvanceDate(),
                advance.getNote(),
                advance.getReversalOf() == null ? null : advance.getReversalOf().getId(),
                reversedIds.contains(advance.getId()),
                advance.getCreatedAt());
    }
}
