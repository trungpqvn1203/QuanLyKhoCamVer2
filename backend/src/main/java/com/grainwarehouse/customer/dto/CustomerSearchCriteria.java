package com.grainwarehouse.customer.dto;

import com.grainwarehouse.common.search.HasActive;
import com.grainwarehouse.common.search.HasKeyword;
import com.grainwarehouse.customer.entity.CustomerType;

/**
 * Tiêu chí lọc danh sách khách, Spring bind từ query string (GET /api/customers?keyword=&type=&active=).
 * keyword khớp mã, tên hoặc SĐT. Mọi trường tùy chọn: null = không lọc; type sai giá trị → 400.
 */
public record CustomerSearchCriteria(String keyword, CustomerType type, Boolean active)
        implements HasKeyword, HasActive {
}
