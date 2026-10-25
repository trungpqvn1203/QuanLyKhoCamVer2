package com.grainwarehouse.payroll.dto;

import com.grainwarehouse.payroll.entity.PayrollStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

/**
 * Bảng lương một tháng.
 * finalized = false: tạm tính trực tiếp từ chấm công với đơn giá HIỆN TẠI (BR-PAY-02), payrollId/finalizedAt vắng.
 * finalized = true: đọc từ bản chụp đã chốt — đơn giá lúc chốt.
 * history: các lần chốt đã bị hủy của tháng này (mới nhất trước), để còn dấu vết.
 */
public record PayrollSheetResponse(
        YearMonth month,
        boolean finalized,
        Long payrollId,
        Instant finalizedAt,
        BigDecimal totalAmount,
        List<Line> items,
        List<HistoryEntry> history) {

    public record Line(
            Long employeeId,
            String employeeCode,
            String employeeName,
            boolean employeeActive,
            BigDecimal workUnits,
            BigDecimal unloadTons,
            BigDecimal deliveryTons,
            BigDecimal dailyWage,
            BigDecimal unloadTonRate,
            BigDecimal deliveryTonRate,
            BigDecimal dayAmount,
            BigDecimal unloadAmount,
            BigDecimal deliveryAmount,
            BigDecimal totalAmount) {
    }

    public record HistoryEntry(
            Long payrollId,
            PayrollStatus status,
            BigDecimal totalAmount,
            Instant finalizedAt,
            Instant cancelledAt,
            String cancelReason) {
    }
}
