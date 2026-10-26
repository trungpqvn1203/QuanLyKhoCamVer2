package com.grainwarehouse.employee.dto;

import java.math.BigDecimal;
import java.util.List;

/** Số dư tạm ứng hiện có của một nhân viên và lịch sử ứng / hoàn (mới nhất trước). Mọi thao tác ứng / hoàn trả lại response này. */
public record EmployeeAdvanceLedgerResponse(
        Long employeeId,
        String employeeCode,
        String employeeName,
        boolean employeeActive,
        BigDecimal advanceBalance,
        List<EmployeeAdvanceResponse> advances) {
}
