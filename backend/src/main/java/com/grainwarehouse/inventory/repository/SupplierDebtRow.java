package com.grainwarehouse.inventory.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Một dòng kết quả GROUP BY công nợ theo NCC (projection của StockDocumentRepository). */
public interface SupplierDebtRow {

    Long getSupplierId();

    BigDecimal getOutstandingAmount();

    Long getOpenReceiptCount();

    LocalDate getOldestDocumentDate();
}
