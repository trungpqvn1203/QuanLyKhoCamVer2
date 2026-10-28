package com.grainwarehouse.inventory.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Tồn của một SP: tổng + từng kho (GET /api/inventory/products/{id}). */
public record ProductStockDetailResponse(
        Long productId,
        String productCode,
        String productName,
        String unitName,
        BigDecimal netWeightKg,
        Integer minStockQuantity,
        long stockQuantity,
        BigDecimal stockTons,
        long incomingQuantity,
        StockStatus stockStatus,
        List<WarehouseStock> warehouses) {

    /**
     * Một kho có tồn > 0 hoặc có hàng đang về. warehouseActive = false: kho đã ngừng nhưng hàng vẫn nằm đó,
     * vẫn tính vào tổng (BR-STK-01). updatedAt: lần đổi tồn gần nhất (null nếu kho chỉ có hàng đang về).
     */
    public record WarehouseStock(
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            boolean warehouseActive,
            long quantity,
            BigDecimal tons,
            long incomingQuantity,
            Instant updatedAt) {
    }
}
