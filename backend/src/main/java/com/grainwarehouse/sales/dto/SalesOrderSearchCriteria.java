package com.grainwarehouse.sales.dto;

import com.grainwarehouse.common.search.HasKeyword;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.sales.entity.OrderChannel;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Tiêu chí lọc danh sách phiếu bán (GET /api/sales-orders?keyword=&status=&warehouseId=&customerId=
 * &documentDateFrom=&documentDateTo=&unpaidOnly=&orderChannel=&deliveryDateFrom=&deliveryDateTo=).
 * Mọi trường tùy chọn: null = không lọc. unpaidOnly=true: chỉ phiếu Hoàn tất còn nợ (BR-SAL-10).
 * deliveryDate*: theo ngày khách muốn giao (BR-SAL-17) — phiếu không hẹn ngày bị loại khi có lọc.
 */
public record SalesOrderSearchCriteria(
        String keyword,
        StockDocumentStatus status,
        Long warehouseId,
        Long customerId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDateTo,
        Boolean unpaidOnly,
        OrderChannel orderChannel,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDateTo) implements HasKeyword {
}
