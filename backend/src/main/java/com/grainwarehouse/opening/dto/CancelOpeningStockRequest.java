package com.grainwarehouse.opening.dto;

import jakarta.validation.constraints.Size;

/** Hủy phiếu nháp: lý do tùy chọn. */
public record CancelOpeningStockRequest(
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason) {
}
