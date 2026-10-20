package com.grainwarehouse.inventory.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Một dòng kết quả GROUP BY công nợ theo khách (projection của StockDocumentRepository). */
public interface CustomerDebtRow {

    Long getCustomerId();

    BigDecimal getOutstandingAmount();

    Long getOpenOrderCount();

    LocalDate getOldestDocumentDate();
}
