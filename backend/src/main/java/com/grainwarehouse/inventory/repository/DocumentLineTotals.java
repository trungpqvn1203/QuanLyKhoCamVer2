package com.grainwarehouse.inventory.repository;

/**
 * Kết quả GROUP BY theo chứng từ: số dòng và tổng số lượng — cho danh sách phiếu (một câu cho cả trang, không N+1).
 * COUNT và SUM của cột INTEGER trả về Long (theo chuẩn JPA).
 */
public interface DocumentLineTotals {

    Long getDocumentId();

    Long getLineCount();

    Long getQuantity();
}
