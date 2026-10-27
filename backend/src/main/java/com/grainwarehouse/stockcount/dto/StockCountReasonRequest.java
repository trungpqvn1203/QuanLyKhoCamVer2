package com.grainwarehouse.stockcount.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Tạo lý do lệch (BR-CNT-05). */
public record StockCountReasonRequest(
        @NotBlank(message = "Tên lý do không được để trống")
        @Size(max = 100, message = "Tên lý do tối đa 100 ký tự")
        String name) {
}
