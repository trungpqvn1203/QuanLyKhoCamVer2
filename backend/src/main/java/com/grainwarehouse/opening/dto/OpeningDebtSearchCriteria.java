package com.grainwarehouse.opening.dto;

import com.grainwarehouse.common.search.HasKeyword;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;

/**
 * Lọc công nợ đầu kỳ (GET /api/opening-customer-debts | /api/opening-supplier-debts?keyword=&status=&partyId=
 * &unpaidOnly=). keyword tìm theo mã chứng từ hoặc tên khách / NCC. unpaidOnly=true: chỉ khoản còn nợ.
 * party lấy theo URL (controller gán bằng withParty), không theo query string.
 */
public record OpeningDebtSearchCriteria(
        OpeningDebtParty party,
        String keyword,
        StockDocumentStatus status,
        Long partyId,
        Boolean unpaidOnly) implements HasKeyword {

    public OpeningDebtSearchCriteria withParty(OpeningDebtParty value) {
        return new OpeningDebtSearchCriteria(value, keyword, status, partyId, unpaidOnly);
    }
}
