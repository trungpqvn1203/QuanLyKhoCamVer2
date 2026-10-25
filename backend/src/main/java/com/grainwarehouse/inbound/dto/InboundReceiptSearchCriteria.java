package com.grainwarehouse.inbound.dto;

import com.grainwarehouse.common.search.HasKeyword;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Tiêu chí lọc danh sách phiếu nhập (GET /api/inbound-receipts?keyword=&status=&warehouseId=&supplierId=
 * &documentDateFrom=&documentDateTo=&unpaidOnly=). Mọi trường tùy chọn: null = không lọc.
 * unpaidOnly=true: chỉ phiếu Hoàn tất còn nợ NCC (BR-SPAY-01).
 * Không có active: phiếu không có trạng thái hoạt động, chỉ có status.
 */
public record InboundReceiptSearchCriteria(
        String keyword,
        StockDocumentStatus status,
        Long warehouseId,
        Long supplierId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDateTo,
        Boolean unpaidOnly) implements HasKeyword {
}
