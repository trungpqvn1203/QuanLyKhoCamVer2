package com.grainwarehouse.portal.dto;

import com.grainwarehouse.sales.dto.SaleRequestLineRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Khách đặt / sửa đơn (BR-POR-07, 09). KHÔNG có customerId — khách lấy từ token — và không có kênh (luôn APP).
 */
public record PortalOrderRequest(
        // Được ở tương lai (đặt trước); không gửi = chưa hẹn ngày
        LocalDate requestedDeliveryDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note,

        @NotEmpty(message = "Đơn đặt phải có ít nhất một sản phẩm")
        @Size(max = 200, message = "Đơn đặt tối đa 200 dòng")
        @Valid
        List<SaleRequestLineRequest> lines) {
}
