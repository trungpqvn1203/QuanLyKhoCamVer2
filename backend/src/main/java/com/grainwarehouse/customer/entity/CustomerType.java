package com.grainwarehouse.customer.entity;

/** Loại khách hàng (BR-CUS-03). Phải khớp CHECK ck_customer_type trong migration V8. */
public enum CustomerType {
    DEALER,
    FARM,
    RETAIL
}
