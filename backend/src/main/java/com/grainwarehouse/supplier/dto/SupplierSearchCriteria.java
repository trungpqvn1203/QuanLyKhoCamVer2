package com.grainwarehouse.supplier.dto;

/**
 * Tiêu chí lọc danh sách NCC, Spring bind từ query string (GET /api/suppliers?keyword=&active=).
 * Mọi trường tùy chọn: null = không lọc. Phân trang/sắp xếp ở Pageable.
 */
public record SupplierSearchCriteria(String keyword, Boolean active) {
}
