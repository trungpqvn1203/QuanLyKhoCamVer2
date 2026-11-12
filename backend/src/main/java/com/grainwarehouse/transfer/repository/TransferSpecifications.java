package com.grainwarehouse.transfer.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.transfer.dto.TransferSearchCriteria;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Lọc phiếu điều chuyển. Luôn kèm type = TRANSFER vì bảng stock_document dùng chung mọi loại chứng từ. */
public final class TransferSpecifications {

    private TransferSpecifications() {
    }

    public static Specification<StockDocument> from(TransferSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.equal("type", StockDocumentType.TRANSFER),
                CommonSpecifications.keywordContains(criteria, "code"),
                CommonSpecifications.equal("status", criteria.status()),
                CommonSpecifications.equal("warehouse.id", criteria.sourceWarehouseId()),
                CommonSpecifications.equal("targetWarehouse.id", criteria.targetWarehouseId()),
                CommonSpecifications.<StockDocument, LocalDate>between(
                        "documentDate", criteria.documentDateFrom(), criteria.documentDateTo()));
    }
}
