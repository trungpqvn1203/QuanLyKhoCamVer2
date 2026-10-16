package com.grainwarehouse.warehouse.entity;

/**
 * Loại kho (BR-WH-01).
 * Phải khớp CHECK ck_warehouse_type trong migration V3.
 */
public enum WarehouseType {
    /** Kho tập kết */
    CENTRAL,
    /** Kho bán lẻ */
    RETAIL
}
