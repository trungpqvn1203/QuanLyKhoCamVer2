package com.grainwarehouse.payroll.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Công nợ hai chiều của một nhân viên (BR-EPAY-01, 03):
 * unpaidSalaryAmount = công ty còn nợ lương; advanceBalance = nhân viên còn nợ tạm ứng.
 * netAmount = unpaidSalaryAmount − advanceBalance: dương = công ty nợ nhân viên, âm = nhân viên đang giữ tạm ứng nhiều hơn lương chưa trả.
 */
public record EmployeeDebtResponse(
        Long employeeId,
        String employeeCode,
        String employeeName,
        boolean employeeActive,
        BigDecimal unpaidSalaryAmount,
        BigDecimal advanceBalance,
        BigDecimal netAmount,
        List<UnpaidMonth> unpaidMonths) {

    /** Một tháng lương chưa trả đủ (cũ nhất trước). */
    public record UnpaidMonth(YearMonth month, BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal outstandingAmount) {
    }
}
