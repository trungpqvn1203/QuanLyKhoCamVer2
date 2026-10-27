package com.grainwarehouse.stockcount.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Một dòng kiểm kê khi lưu nháp. SL sổ sách KHÔNG gửi lên — hệ thống chụp (BR-CNT-03).
 * countedQuantity null = chưa đếm (lưu nháp được, xác nhận thì không). Lý do bắt buộc lúc xác nhận nếu lệch (BR-CNT-05).
 */
public record StockCountLineRequest(
        @NotNull(message = "Sản phẩm không được để trống")
        Long productId,

        @PositiveOrZero(message = "Số lượng đếm không được âm")
        Integer countedQuantity,

        Long reasonId,

        @Size(max = 200, message = "Ghi chú lý do tối đa 200 ký tự")
        String reasonNote) {
}
