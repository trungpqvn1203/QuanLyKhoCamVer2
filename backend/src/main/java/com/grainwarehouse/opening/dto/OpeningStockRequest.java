package com.grainwarehouse.opening.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Tạo / sửa phiếu tồn đầu kỳ nháp (BR-OPN-01). Ngày không gửi = hôm nay; không được ở tương lai.
 * Tối đa 1.000 dòng — một kho có thể có nhiều SP hơn một phiếu nhập thường (giới hạn như import Excel, BR-XLS-04).
 */
public record OpeningStockRequest(
        @NotNull(message = "Kho không được để trống")
        Long warehouseId,

        LocalDate documentDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note,

        @NotEmpty(message = "Phiếu tồn đầu kỳ phải có ít nhất một dòng hàng")
        @Size(max = 1000, message = "Phiếu tồn đầu kỳ tối đa 1.000 dòng hàng")
        @Valid
        List<OpeningStockLineRequest> lines) {
}
