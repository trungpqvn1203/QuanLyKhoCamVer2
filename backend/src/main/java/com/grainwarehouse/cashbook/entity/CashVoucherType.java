package com.grainwarehouse.cashbook.entity;

/** VOUCHER: phiếu thu/chi gốc. REVERSAL: phiếu hoàn một phiếu ghi nhầm, trỏ về phiếu gốc (BR-CASH-04). */
public enum CashVoucherType {
    VOUCHER,
    REVERSAL
}
