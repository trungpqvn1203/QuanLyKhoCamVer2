package com.grainwarehouse.transfer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Tạo / sửa phiếu điều chuyển nháp (BR-TRF-05, 06). Ngày không gửi = hôm nay; không được ở tương lai (BR-TRF-10).
 * Tối đa 1.000 dòng — dọn cả một kho sang kho khác vẫn nằm trong một phiếu (cùng giới hạn với tồn đầu kỳ).
 */
public record TransferRequest(
        @NotNull(message = "Kho nguồn không được để trống")
        Long sourceWarehouseId,

        @NotNull(message = "Kho đích không được để trống")
        Long targetWarehouseId,

        LocalDate documentDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note,

        @NotEmpty(message = "Phiếu điều chuyển phải có ít nhất một dòng hàng")
        @Size(max = 1000, message = "Phiếu điều chuyển tối đa 1.000 dòng hàng")
        @Valid
        List<TransferLineRequest> lines) {
}
