package com.grainwarehouse.stockcount.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Lưu nháp phiếu kiểm kê: thay toàn bộ dòng (bỏ dòng = không kiểm SP đó). Kho không đổi được sau khi lập phiếu —
 * SL sổ sách đã chụp theo kho đó.
 */
public record StockCountUpdateRequest(
        LocalDate documentDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note,

        @NotEmpty(message = "Phiếu kiểm kê phải có ít nhất một dòng")
        @Size(max = 1000, message = "Phiếu kiểm kê tối đa 1.000 dòng")
        @Valid
        List<StockCountLineRequest> lines) {
}
