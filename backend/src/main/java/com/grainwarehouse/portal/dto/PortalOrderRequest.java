package com.grainwarehouse.portal.dto;

import com.grainwarehouse.portal.service.PortalOrderLimits;
import com.grainwarehouse.sales.dto.SaleRequestLineRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Khách đặt / sửa đơn (BR-POR-07, 09, 12). KHÔNG có customerId — khách lấy từ token — và không có kênh (luôn APP).
 * note = lời khách → lưu vào customer_note, không bao giờ vào ghi chú nội bộ của cửa hàng (BR-POR-11).
 */
public record PortalOrderRequest(
        // Từ hôm nay đến tối đa 60 ngày tới (kiểm ở PortalOrderService vì cần "hôm nay" theo giờ VN); không gửi = chưa hẹn
        LocalDate requestedDeliveryDate,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note,

        @NotEmpty(message = "Đơn đặt phải có ít nhất một sản phẩm")
        @Size(max = PortalOrderLimits.MAX_LINES,
                message = "Đơn đặt tối đa " + PortalOrderLimits.MAX_LINES + " sản phẩm")
        @Valid
        List<@NotNull(message = "Dòng đặt hàng không được để trống") PortalOrderLineRequest> lines) {

    public List<SaleRequestLineRequest> saleRequestLines() {
        return lines.stream().map(PortalOrderLineRequest::toSaleRequestLine).toList();
    }
}
