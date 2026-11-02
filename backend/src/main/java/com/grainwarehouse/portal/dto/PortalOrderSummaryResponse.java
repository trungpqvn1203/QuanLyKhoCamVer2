package com.grainwarehouse.portal.dto;

import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.sales.entity.OrderChannel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static com.grainwarehouse.inventory.entity.StockDocumentStatus.COMPLETED;
import static com.grainwarehouse.inventory.entity.StockDocumentStatus.DRAFT;

/** Một dòng "Đơn của tôi" (BR-POR-08). Tổng tiền chỉ có khi cửa hàng đã xác nhận. */
public record PortalOrderSummaryResponse(
        Long id,
        String code,
        StockDocumentStatus status,
        OrderChannel orderChannel,
        LocalDate orderDate,
        LocalDate requestedDeliveryDate,
        BigDecimal totalAmount,
        Instant createdAt) {

    public static PortalOrderSummaryResponse from(StockDocument document) {
        boolean priced = document.getStatus() == DRAFT || document.getStatus() == COMPLETED;
        return new PortalOrderSummaryResponse(
                document.getId(),
                document.getCode(),
                document.getStatus(),
                document.getOrderChannel(),
                document.getDocumentDate(),
                document.getRequestedDeliveryDate(),
                priced ? document.getTotalAmount() : null,
                document.getCreatedAt());
    }
}
