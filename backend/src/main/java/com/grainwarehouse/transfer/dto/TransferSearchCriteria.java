package com.grainwarehouse.transfer.dto;

import com.grainwarehouse.common.search.HasKeyword;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Tiêu chí lọc danh sách phiếu điều chuyển (GET /api/transfers?keyword=&status=&sourceWarehouseId=
 * &targetWarehouseId=&documentDateFrom=&documentDateTo=). Mọi trường tùy chọn: null = không lọc.
 * keyword tìm theo mã phiếu.
 */
public record TransferSearchCriteria(
        String keyword,
        StockDocumentStatus status,
        Long sourceWarehouseId,
        Long targetWarehouseId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDateTo) implements HasKeyword {
}
