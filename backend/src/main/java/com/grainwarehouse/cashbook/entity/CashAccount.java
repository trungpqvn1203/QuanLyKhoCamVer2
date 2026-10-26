package com.grainwarehouse.cashbook.entity;

/**
 * Nơi giữ tiền (BR-CASH-01): một quỹ tiền mặt và một tài khoản ngân hàng.
 * Thu/chi bằng tiền mặt (CASH) vào quỹ, bằng chuyển khoản (BANK_TRANSFER) vào tài khoản.
 */
public enum CashAccount {
    CASH,
    BANK
}
