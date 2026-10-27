package com.grainwarehouse.inventory.repository;

/**
 * Tổng hợp một phiếu kiểm kê cho danh sách (một câu GROUP BY cho cả trang): số dòng, số dòng đã đếm, số dòng lệch,
 * tổng thừa, tổng thiếu. COUNT / SUM trả về Long (theo chuẩn JPA).
 */
public interface StockCountTotals {

    Long getDocumentId();

    Long getLineCount();

    Long getCountedLineCount();

    Long getDifferenceLineCount();

    Long getSurplusQuantity();

    Long getShortageQuantity();
}
