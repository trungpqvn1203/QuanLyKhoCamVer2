package com.grainwarehouse.stockcount.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.stockcount.dto.StockCountSearchCriteria;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Lọc phiếu kiểm kê. Luôn kèm type = STOCK_COUNT vì bảng stock_document dùng chung mọi loại chứng từ. */
public final class StockCountSpecifications {

    private StockCountSpecifications() {
    }

    public static Specification<StockDocument> from(StockCountSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.equal("type", StockDocumentType.STOCK_COUNT),
                CommonSpecifications.keywordContains(criteria, "code"),
                CommonSpecifications.equal("status", criteria.status()),
                CommonSpecifications.equal("warehouse.id", criteria.warehouseId()),
                CommonSpecifications.<StockDocument, LocalDate>between(
                        "documentDate", criteria.documentDateFrom(), criteria.documentDateTo()));
    }
}
