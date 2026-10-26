package com.grainwarehouse.cashbook.repository;

import com.grainwarehouse.cashbook.dto.CashVoucherSearchCriteria;
import com.grainwarehouse.cashbook.entity.CashVoucher;
import com.grainwarehouse.common.search.CommonSpecifications;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Ghép tiêu chí lọc phiếu thu/chi thành điều kiện SQL; thêm tiêu chí mới = thêm một dòng ở đây. */
public final class CashVoucherSpecifications {

    private CashVoucherSpecifications() {
    }

    public static Specification<CashVoucher> from(CashVoucherSearchCriteria criteria) {
        return Specification.allOf(
                CommonSpecifications.keywordContains(criteria, "code", "counterparty"),
                CommonSpecifications.equal("direction", criteria.direction()),
                CommonSpecifications.equal("account", criteria.account()),
                CommonSpecifications.equal("category.id", criteria.categoryId()),
                CommonSpecifications.<CashVoucher, LocalDate>between(
                        "voucherDate", criteria.voucherDateFrom(), criteria.voucherDateTo()));
    }
}
