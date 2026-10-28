package com.grainwarehouse.inventory.dto;

import com.grainwarehouse.product.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một dòng trong danh sách SP kèm tồn: đủ mọi trường của ProductResponse (màn hình Sản phẩm dùng luôn
 * để xem/sửa) + số tồn. Các trường warehouseXxx chỉ có khi lọc theo kho (null → bỏ khỏi JSON).
 */
public record ProductStockResponse(
        Long id,
        String code,
        String name,
        Long categoryId,
        String categoryName,
        Long unitId,
        String unitName,
        BigDecimal netWeightKg,
        String description,
        Integer minStockQuantity,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long stockQuantity,
        BigDecimal stockTons,
        long incomingQuantity,
        StockStatus stockStatus,
        Long warehouseId,
        Long warehouseStockQuantity,
        BigDecimal warehouseStockTons,
        Long warehouseIncomingQuantity) {

    /** Phải gọi bên trong transaction vì category là LAZY. scoped = null khi không lọc theo kho. */
    public static ProductStockResponse from(Product product, StockFigures total, Long warehouseId, StockFigures scoped) {
        return new ProductStockResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getUnit().getId(),
                product.getUnit().getName(),
                product.getNetWeightKg(),
                product.getDescription(),
                product.getMinStockQuantity(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                total.quantity(),
                total.tons(),
                total.incoming(),
                StockStatus.of(total.quantity(), product.getMinStockQuantity()),
                warehouseId,
                scoped == null ? null : scoped.quantity(),
                scoped == null ? null : scoped.tons(),
                scoped == null ? null : scoped.incoming());
    }
}
