package com.grainwarehouse.stockcount.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT lý do lệch: đổi tên, ngừng dùng / mở lại. */
public record UpdateStockCountReasonRequest(
        @NotBlank(message = "Tên lý do không được để trống")
        @Size(max = 100, message = "Tên lý do tối đa 100 ký tự")
        String name,

        @NotNull(message = "Trạng thái không được để trống")
        Boolean active) {
}
