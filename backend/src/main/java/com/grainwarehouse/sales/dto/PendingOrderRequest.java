package com.grainwarehouse.sales.dto;

import com.grainwarehouse.sales.entity.OrderChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Body ghi (POST) và sửa (PUT) đơn khách đặt — phiếu bán Chờ xác nhận (BR-SAL-17, 18).
 * Không có kho và giá: kho điền lúc xác nhận. Khi sửa, customerId phải giữ nguyên.
 */
public record PendingOrderRequest(
        @NotNull(message = "Khách hàng không được để trống")
        Long customerId,

        @NotNull(message = "Kênh nhận đơn không được để trống")
        OrderChannel orderChannel,

        // Được ở tương lai (đặt trước); không gửi = chưa hẹn ngày
        LocalDate requestedDeliveryDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note,

        @NotEmpty(message = "Đơn đặt phải có ít nhất một sản phẩm")
        @Size(max = 200, message = "Đơn đặt tối đa 200 dòng")
        @Valid
        List<SaleRequestLineRequest> lines) {
}
