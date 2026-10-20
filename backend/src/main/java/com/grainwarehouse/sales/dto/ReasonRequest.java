package com.grainwarehouse.sales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lý do bắt buộc: đảo phiếu bán (BR-SAL-05) và hoàn khoản thu (BR-SAL-13) — lịch sử phải giải thích được. */
public record ReasonRequest(
        @NotBlank(message = "Lý do không được để trống")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason) {
}
