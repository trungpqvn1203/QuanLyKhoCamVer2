package com.grainwarehouse.inventory.entity;

/** Loại chứng từ kho: phiếu nhập, phiếu bán (V13). TRANSFER thêm ở module sau (kèm migration nới CHECK). */
public enum StockDocumentType {
    INBOUND,
    OUTBOUND
}
