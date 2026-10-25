package com.grainwarehouse.inbound.entity;

/** PAYMENT: khoản trả cho NCC. REVERSAL: hoàn một khoản trả ghi nhầm, trỏ về khoản gốc (BR-SPAY-03). */
public enum SupplierPaymentType {
    PAYMENT,
    REVERSAL
}
