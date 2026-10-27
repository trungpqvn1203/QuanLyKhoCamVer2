package com.grainwarehouse.opening.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.opening.dto.OpeningStockSearchCriteria;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Lọc phiếu tồn đầu kỳ. Luôn kèm type = OPENING_STOCK vì bảng stock_document dùng chung mọi loại chứng từ. */
public final class OpeningStockSpecifications {

    private OpeningStockSpecifications() {
    }

    public static Specification<StockDocument> from(OpeningStockSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.equal("type", StockDocumentType.OPENING_STOCK),
                CommonSpecifications.keywordContains(criteria, "code"),
                CommonSpecifications.equal("status", criteria.status()),
                CommonSpecifications.equal("warehouse.id", criteria.warehouseId()),
                CommonSpecifications.<StockDocument, LocalDate>between(
                        "documentDate", criteria.documentDateFrom(), criteria.documentDateTo()));
    }
}
