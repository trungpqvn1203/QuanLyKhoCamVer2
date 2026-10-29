package com.grainwarehouse.report.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Báo cáo tồn hiện tại dạng bảng SP × kho (BR-RPT-04). Cộng chung số lượng khác đơn vị (bao + kg) là vô nghĩa
 * → dòng tổng của từng kho chỉ có số tấn.
 */
public record StockByWarehouseResponse(
        List<WarehouseColumn> warehouses,
        List<Row> rows,
        // warehouseId → tổng tấn tại kho đó (theo các dòng đang hiện)
        Map<Long, BigDecimal> warehouseTons,
        BigDecimal totalTons) {

    /** Kho đang hoạt động, hoặc đã ngừng nhưng vẫn còn hàng (BR-STK-01). */
    public record WarehouseColumn(Long id, String code, String name, boolean active) {
    }

    public record Row(
            Long productId,
            String productCode,
            String productName,
            String categoryName,
            String unitName,
            boolean productActive,
            // warehouseId → số lượng; kho không có hàng thì không có khóa
            Map<Long, Long> quantities,
            long totalQuantity,
            BigDecimal totalTons) {
    }
}
