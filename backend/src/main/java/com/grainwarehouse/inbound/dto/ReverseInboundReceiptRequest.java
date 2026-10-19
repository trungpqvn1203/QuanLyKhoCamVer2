package com.grainwarehouse.inbound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lý do đảo phiếu đã hoàn tất — bắt buộc, vì đảo làm thay đổi tồn và lịch sử cần giải thích được. */
public record ReverseInboundReceiptRequest(
        @NotBlank(message = "Lý do đảo phiếu không được để trống")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason) {
}
