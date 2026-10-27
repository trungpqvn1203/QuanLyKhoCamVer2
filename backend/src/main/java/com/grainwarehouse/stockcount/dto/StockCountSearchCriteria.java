package com.grainwarehouse.stockcount.dto;

import com.grainwarehouse.common.search.HasKeyword;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Lọc phiếu kiểm kê (GET /api/stock-counts?keyword=&status=&warehouseId=&documentDateFrom=&documentDateTo=).
 * keyword tìm theo mã phiếu. Mọi trường tùy chọn.
 */
public record StockCountSearchCriteria(
        String keyword,
        StockDocumentStatus status,
        Long warehouseId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDateTo) implements HasKeyword {
}
