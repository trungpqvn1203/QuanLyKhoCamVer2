package com.grainwarehouse.payroll.entity;

/**
 * Hình thức tất toán một phần lương (BR-EPAY-02). CASH / BANK_TRANSFER là tiền công ty chi ra;
 * ADVANCE_DEDUCTION là trừ vào số dư tạm ứng của nhân viên — không có tiền ra khỏi quỹ.
 * Phải khớp CHECK ck_payroll_payment_method trong migration V18.
 */
public enum PayrollPaymentMethod {
    CASH,
    BANK_TRANSFER,
    ADVANCE_DEDUCTION
}
