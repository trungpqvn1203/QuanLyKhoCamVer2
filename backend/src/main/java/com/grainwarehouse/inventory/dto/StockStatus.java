package com.grainwarehouse.inventory.dto;

/**
 * Tình trạng tồn của một SP, tính trên TỔNG tồn mọi kho (BR-STK-01, BR-STK-04):
 * - OUT: tổng = 0
 * - LOW: 0 < tổng ≤ mức tối thiểu (chỉ khi SP có đặt mức tối thiểu)
 * - IN_STOCK: còn lại
 * Cùng một quy tắc được viết hai lần: {@link #of} (Java, cho response) và ProductStockSpecifications
 * (SQL, cho bộ lọc) — ProductStockApiTest kiểm tra hai bên khớp nhau.
 */
public enum StockStatus {
    IN_STOCK,
    LOW,
    OUT;

    public static StockStatus of(long totalQuantity, Integer minStockQuantity) {
        if (totalQuantity == 0) {
            return OUT;
        }
        if (minStockQuantity != null && totalQuantity <= minStockQuantity) {
            return LOW;
        }
        return IN_STOCK;
    }
}
