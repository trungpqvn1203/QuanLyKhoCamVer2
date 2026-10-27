package com.grainwarehouse.inventory.entity;

/**
 * Loại chứng từ: phiếu nhập, phiếu bán (V13), phiếu tồn đầu kỳ (V20), công nợ đầu kỳ khách / NCC (V21 — chứng từ
 * không có kho, không có dòng hàng), kiểm kê (V22). TRANSFER thêm ở module sau (kèm migration nới CHECK).
 */
public enum StockDocumentType {
    INBOUND,
    OUTBOUND,
    OPENING_STOCK,
    OPENING_CUSTOMER_DEBT,
    OPENING_SUPPLIER_DEBT,
    STOCK_COUNT
}
