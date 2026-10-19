package com.grainwarehouse.product.dto;

import com.grainwarehouse.common.search.HasActive;
import com.grainwarehouse.common.search.HasKeyword;

/**
 * Tiêu chí lọc danh sách sản phẩm, Spring bind từ query string (GET /api/products?keyword=&categoryId=&active=).
 * Mọi trường tùy chọn: null = không lọc. Sai kiểu (categoryId=abc) → 400 VALIDATION_FAILED.
 * Phân trang/sắp xếp không nằm ở đây mà ở Pageable (page, size, sort) — contract-spec.
 */
public record ProductSearchCriteria(String keyword, Long categoryId, Boolean active) implements HasKeyword, HasActive {
}
