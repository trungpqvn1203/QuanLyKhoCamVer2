package com.grainwarehouse.sales.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import com.grainwarehouse.sales.dto.SalesOrderSearchCriteria;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ghép tiêu chí lọc phiếu bán thành điều kiện SQL. Luôn kèm type = OUTBOUND vì bảng stock_document
 * dùng chung cho mọi loại chứng từ.
 */
public final class SalesOrderSpecifications {

    private SalesOrderSpecifications() {
    }

    public static Specification<StockDocument> from(SalesOrderSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.equal("type", StockDocumentType.OUTBOUND),
                CommonSpecifications.keywordContains(criteria, "code"),
                CommonSpecifications.equal("status", criteria.status()),
                CommonSpecifications.equal("warehouse.id", criteria.warehouseId()),
                CommonSpecifications.equal("customer.id", criteria.customerId()),
                CommonSpecifications.<StockDocument, LocalDate>between(
                        "documentDate", criteria.documentDateFrom(), criteria.documentDateTo()),
                Boolean.TRUE.equals(criteria.unpaidOnly()) ? unpaid() : null);
    }

    // BR-SAL-10: phiếu Hoàn tất còn nợ (đã thu < tổng phiếu). Phiếu tổng 0 đ (hàng tặng) không bao giờ nợ.
    private static Specification<StockDocument> unpaid() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), StockDocumentStatus.COMPLETED),
                cb.lessThan(root.<BigDecimal>get("paidAmount"), root.<BigDecimal>get("totalAmount")));
    }
}
