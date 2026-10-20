package com.grainwarehouse.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Body cho cả tạo (POST) và sửa (PUT) phiếu bán nháp. PUT thay toàn bộ danh sách dòng.
 * Không có code (tự sinh PX, BR-SAL-03), status và paidAmount (chỉ đổi qua hành động riêng).
 */
public record SalesOrderRequest(
        @NotNull(message = "Khách hàng không được để trống")
        Long customerId,

        @NotNull(message = "Kho xuất không được để trống")
        Long warehouseId,

        // Không gửi = hôm nay (giờ Việt Nam); không được ở tương lai (BR-SAL-04)
        LocalDate documentDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note,

        @NotEmpty(message = "Phiếu bán phải có ít nhất một dòng hàng")
        @Size(max = 200, message = "Phiếu bán tối đa 200 dòng hàng")
        @Valid
        List<SalesOrderLineRequest> lines) {
}
