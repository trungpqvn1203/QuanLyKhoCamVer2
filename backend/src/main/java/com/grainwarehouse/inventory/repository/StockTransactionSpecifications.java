package com.grainwarehouse.inventory.repository;

import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.inventory.dto.StockTransactionSearchCriteria;
import com.grainwarehouse.inventory.entity.StockTransaction;
import org.springframework.data.jpa.domain.Specification;

/** Ghép tiêu chí lọc lịch sử biến động tồn thành điều kiện SQL. */
public final class StockTransactionSpecifications {

    private StockTransactionSpecifications() {
    }

    public static Specification<StockTransaction> from(StockTransactionSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.equal("product.id", criteria.productId()),
                CommonSpecifications.equal("warehouse.id", criteria.warehouseId()),
                CommonSpecifications.equal("type", criteria.type()),
                CommonSpecifications.equal("documentItem.document.type", criteria.documentType()),
                CommonSpecifications.keywordContains(criteria, "documentItem.document.code"),
                CommonSpecifications.instantWithinDates("createdAt", criteria.createdFrom(), criteria.createdTo()));
    }
}
