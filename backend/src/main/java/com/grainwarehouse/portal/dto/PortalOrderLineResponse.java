package com.grainwarehouse.portal.dto;

import com.grainwarehouse.sales.dto.SalesOrderLineResponse;

import java.math.BigDecimal;

/** Dòng cửa hàng đã chốt (BR-POR-08) — chỉ có sau khi đơn được xác nhận. */
public record PortalOrderLineResponse(
        Long productId,
        String productCode,
        String productName,
        String unitName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount) {

    public static PortalOrderLineResponse from(SalesOrderLineResponse line) {
        return new PortalOrderLineResponse(line.productId(), line.productCode(), line.productName(), line.unitName(),
                line.quantity(), line.unitPrice(), line.lineAmount());
    }
}
