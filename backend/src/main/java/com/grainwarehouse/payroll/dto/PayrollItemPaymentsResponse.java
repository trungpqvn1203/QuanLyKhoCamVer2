package com.grainwarehouse.payroll.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Tình trạng trả lương của một nhân viên trong một tháng đã chốt: tổng lương, đã trả, còn nợ, số dư tạm ứng hiện có
 * (để biết còn trừ được bao nhiêu) và lịch sử trả. Mọi thao tác trả / hoàn đều trả lại response này.
 * tracksDebt = false: bảng lương chốt trước module công nợ nhân viên, coi như đã trả đủ (BR-EPAY-05).
 */
public record PayrollItemPaymentsResponse(
        YearMonth month,
        Long employeeId,
        String employeeCode,
        String employeeName,
        boolean employeeActive,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        boolean tracksDebt,
        BigDecimal advanceBalance,
        List<PayrollPaymentResponse> payments) {
}
