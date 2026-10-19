package com.grainwarehouse.inbound.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inbound.dto.InboundReceiptSearchCriteria;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Ghép tiêu chí lọc phiếu nhập thành điều kiện SQL. Luôn kèm type = INBOUND vì bảng stock_document
 * dùng chung cho mọi loại chứng từ.
 */
public final class InboundReceiptSpecifications {

    private InboundReceiptSpecifications() {
    }

    public static Specification<StockDocument> from(InboundReceiptSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.equal("type", StockDocumentType.INBOUND),
                CommonSpecifications.keywordContains(criteria, "code"),
                CommonSpecifications.equal("status", criteria.status()),
                CommonSpecifications.equal("warehouse.id", criteria.warehouseId()),
                CommonSpecifications.equal("supplier.id", criteria.supplierId()),
                // documentDate là LocalDate (không có giờ) → between hai đầu đều tính là đúng, không cần instantWithinDates
                CommonSpecifications.<StockDocument, LocalDate>between(
                        "documentDate", criteria.documentDateFrom(), criteria.documentDateTo()));
    }
}
