package com.grainwarehouse.payroll.entity;

/** PAYMENT: khoản trả lương. REVERSAL: hoàn một khoản trả ghi nhầm, trỏ về khoản gốc (BR-EPAY-04). */
public enum PayrollPaymentType {
    PAYMENT,
    REVERSAL
}
