package com.grainwarehouse.inbound.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inbound.dto.InboundReceiptSearchCriteria;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
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
                        "documentDate", criteria.documentDateFrom(), criteria.documentDateTo()),
                Boolean.TRUE.equals(criteria.unpaidOnly()) ? unpaid() : null);
    }

    /** BR-SPAY-01: phiếu Hoàn tất, có theo dõi công nợ, đã trả < tổng phiếu. */
    private static Specification<StockDocument> unpaid() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), StockDocumentStatus.COMPLETED),
                cb.isTrue(root.get("tracksDebt")),
                cb.lessThan(root.<BigDecimal>get("paidAmount"), root.<BigDecimal>get("totalAmount")));
    }
}
