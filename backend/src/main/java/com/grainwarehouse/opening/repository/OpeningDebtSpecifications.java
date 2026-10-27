package com.grainwarehouse.opening.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.opening.dto.OpeningDebtParty;
import com.grainwarehouse.opening.dto.OpeningDebtSearchCriteria;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/** Lọc công nợ đầu kỳ. Luôn kèm type của đúng loại (khách / NCC) vì bảng stock_document dùng chung. */
public final class OpeningDebtSpecifications {

    private OpeningDebtSpecifications() {
    }

    public static Specification<StockDocument> from(OpeningDebtSearchCriteria criteria) {
        OpeningDebtParty party = criteria.party();
        String partyPath = party == OpeningDebtParty.CUSTOMER ? "customer" : "supplier";
        return Specification.allOf(
                CommonSpecifications.equal("type", party.documentType()),
                CommonSpecifications.keywordContains(criteria, "code", partyPath + ".name"),
                CommonSpecifications.equal("status", criteria.status()),
                CommonSpecifications.equal(partyPath + ".id", criteria.partyId()),
                Boolean.TRUE.equals(criteria.unpaidOnly()) ? unpaid() : null);
    }

    /** Còn nợ: Hoàn tất và đã thu/trả < số nợ. */
    private static Specification<StockDocument> unpaid() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), StockDocumentStatus.COMPLETED),
                cb.lessThan(root.<BigDecimal>get("paidAmount"), root.<BigDecimal>get("totalAmount")));
    }
}
