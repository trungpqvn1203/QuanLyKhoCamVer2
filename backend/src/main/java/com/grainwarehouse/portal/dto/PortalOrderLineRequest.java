package com.grainwarehouse.portal.dto;

import com.grainwarehouse.portal.service.PortalOrderLimits;
import com.grainwarehouse.sales.dto.SaleRequestLineRequest;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Một dòng khách đặt trên app (BR-POR-12). Tách khỏi SaleRequestLineRequest của nhân viên vì app có giới hạn riêng.
 * quantity là BigDecimal thay vì Integer: Jackson mặc định lặng lẽ cắt 1.5 thành 1 khi đọc vào Integer — khách đặt
 * 1,5 bao lại nhận đơn 1 bao. Đọc BigDecimal rồi kiểm @Digits(fraction = 0) thì báo lỗi rõ ràng.
 */
public record PortalOrderLineRequest(
        @NotNull(message = "Sản phẩm không được để trống")
        Long productId,

        @NotNull(message = "Số lượng không được để trống")
        @Positive(message = "Số lượng phải lớn hơn 0")
        @Digits(integer = 9, fraction = 0, message = "Số lượng phải là số nguyên")
        @Max(value = PortalOrderLimits.MAX_LINE_QUANTITY,
                message = "Mỗi sản phẩm đặt tối đa " + PortalOrderLimits.MAX_LINE_QUANTITY_TEXT
                        + " — đơn lớn hơn vui lòng gọi cửa hàng")
        BigDecimal quantity) {

    public SaleRequestLineRequest toSaleRequestLine() {
        return new SaleRequestLineRequest(productId, quantity.intValueExact());
    }
}
