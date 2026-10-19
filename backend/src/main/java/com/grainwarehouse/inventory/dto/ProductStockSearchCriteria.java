package com.grainwarehouse.inventory.dto;

import com.grainwarehouse.common.search.HasActive;
import com.grainwarehouse.common.search.HasKeyword;

/**
 * Tiêu chí danh sách SP kèm tồn (GET /api/inventory/products?keyword=&categoryId=&active=&warehouseId=&stockStatus=).
 * keyword/categoryId/active: giống GET /api/products.
 * warehouseId: KHÔNG lọc bớt SP — chỉ thêm số tồn tại kho đó vào response (BR-STK-05).
 * stockStatus: lọc theo tình trạng tồn trên tổng mọi kho.
 */
public record ProductStockSearchCriteria(
        String keyword,
        Long categoryId,
        Boolean active,
        Long warehouseId,
        StockStatus stockStatus) implements HasKeyword, HasActive {
}
