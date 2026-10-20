package com.grainwarehouse.inventory.dto;

import com.grainwarehouse.common.search.HasKeyword;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.inventory.entity.StockTransactionType;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Tiêu chí lịch sử biến động tồn (GET /api/inventory/transactions?productId=&warehouseId=&type=&documentType=
 * &keyword=&createdFrom=&createdTo=). Mọi trường tùy chọn: null = không lọc.
 * keyword: mã chứng từ gây ra biến động (PN…, PX…).
 * createdFrom/createdTo: ngày ghi sổ (lúc hoàn tất / đảo phiếu, giờ VN) — không phải ngày chứng từ,
 * vì tồn trước/sau đi theo thứ tự ghi sổ.
 */
public record StockTransactionSearchCriteria(
        Long productId,
        Long warehouseId,
        StockTransactionType type,
        StockDocumentType documentType,
        String keyword,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo) implements HasKeyword {
}
