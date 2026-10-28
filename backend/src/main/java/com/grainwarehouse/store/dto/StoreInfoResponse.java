package com.grainwarehouse.store.dto;

import com.grainwarehouse.store.entity.StoreInfo;

import java.time.Instant;

/** configured = false: chưa nhập thông tin cửa hàng → các trường khác bị bỏ khỏi JSON (NON_NULL). */
public record StoreInfoResponse(
        boolean configured,
        String name,
        String address,
        String phone,
        String receiptFooter,
        Instant updatedAt) {

    public static StoreInfoResponse from(StoreInfo info) {
        if (info == null) {
            return new StoreInfoResponse(false, null, null, null, null, null);
        }
        return new StoreInfoResponse(true, info.getName(), info.getAddress(), info.getPhone(),
                info.getReceiptFooter(), info.getUpdatedAt());
    }
}
