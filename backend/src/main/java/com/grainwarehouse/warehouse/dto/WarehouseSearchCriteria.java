package com.grainwarehouse.warehouse.dto;

import com.grainwarehouse.common.search.HasActive;
import com.grainwarehouse.common.search.HasKeyword;

/**
 * Tiêu chí lọc danh sách kho, Spring bind từ query string (GET /api/warehouses?keyword=&active=).
 * Mọi trường tùy chọn: null = không lọc. Phân trang/sắp xếp ở Pageable.
 */
public record WarehouseSearchCriteria(String keyword, Boolean active) implements HasKeyword, HasActive {
}
