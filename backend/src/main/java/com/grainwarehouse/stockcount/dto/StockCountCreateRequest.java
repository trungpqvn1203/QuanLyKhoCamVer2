package com.grainwarehouse.stockcount.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Lập phiếu kiểm kê (BR-CNT-01, 02): chỉ cần kho — hệ thống nạp sẵn mọi SP đang có tồn tại kho và chụp SL sổ sách.
 * Ngày không gửi = hôm nay; không được ở tương lai.
 */
public record StockCountCreateRequest(
        @NotNull(message = "Kho không được để trống")
        Long warehouseId,

        LocalDate documentDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note) {
}
