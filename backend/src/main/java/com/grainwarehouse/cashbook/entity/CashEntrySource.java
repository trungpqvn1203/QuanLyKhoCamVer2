package com.grainwarehouse.cashbook.entity;

/**
 * Nguồn của một dòng sổ quỹ (BR-CASH-02) — khớp cột source của VIEW cash_flow_entry (migration V19).
 * Trả lương chỉ gồm tiền mặt / chuyển khoản: dòng trừ tạm ứng không có tiền ra nên không có trong sổ.
 */
public enum CashEntrySource {
    SALE_PAYMENT,
    SUPPLIER_PAYMENT,
    PAYROLL_PAYMENT,
    EMPLOYEE_ADVANCE,
    CASH_VOUCHER
}
