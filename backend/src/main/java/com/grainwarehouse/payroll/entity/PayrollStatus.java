package com.grainwarehouse.payroll.entity;

/** BR-PAY-03. Phải khớp CHECK ck_payroll_status (V12). Tháng chưa chốt không có dòng payroll nào. */
public enum PayrollStatus {
    FINALIZED,
    CANCELLED
}
