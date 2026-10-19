package com.grainwarehouse.inbound.dto;

import jakarta.validation.constraints.Size;

/** Lý do hủy phiếu nháp — tùy chọn (phiếu nháp chưa ảnh hưởng tồn). */
public record CancelInboundReceiptRequest(
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason) {
}
