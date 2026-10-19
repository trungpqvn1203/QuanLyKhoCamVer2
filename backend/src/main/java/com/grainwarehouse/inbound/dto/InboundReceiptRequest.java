package com.grainwarehouse.inbound.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Body cho cả tạo phiếu nháp (POST) và sửa phiếu nháp (PUT) — hai thao tác nhận cùng dữ liệu.
 * PUT thay toàn bộ danh sách dòng: dòng không còn trong request bị xóa khỏi phiếu nháp.
 * Không có trường code (tự sinh, BR-IMP-07) và status (chỉ đổi qua complete/cancel/reverse).
 */
public record InboundReceiptRequest(
        @NotNull(message = "Nhà cung cấp không được để trống")
        Long supplierId,

        @NotNull(message = "Kho nhận không được để trống")
        Long warehouseId,

        // Không gửi = hôm nay (giờ Việt Nam). Kiểm tra "không ở tương lai" ở service (BR-IMP-08)
        LocalDate documentDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note,

        @NotEmpty(message = "Phiếu nhập phải có ít nhất một dòng hàng")
        @Size(max = 200, message = "Phiếu nhập tối đa 200 dòng hàng")
        @Valid
        List<InboundReceiptLineRequest> lines) {
}
