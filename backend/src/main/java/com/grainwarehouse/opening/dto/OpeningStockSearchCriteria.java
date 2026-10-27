package com.grainwarehouse.opening.dto;

import com.grainwarehouse.common.search.HasKeyword;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Tiêu chí lọc danh sách phiếu tồn đầu kỳ (GET /api/opening-stocks?keyword=&status=&warehouseId=
 * &documentDateFrom=&documentDateTo=). Mọi trường tùy chọn: null = không lọc. keyword tìm theo mã phiếu.
 */
public record OpeningStockSearchCriteria(
        String keyword,
        StockDocumentStatus status,
        Long warehouseId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDateTo) implements HasKeyword {
}
