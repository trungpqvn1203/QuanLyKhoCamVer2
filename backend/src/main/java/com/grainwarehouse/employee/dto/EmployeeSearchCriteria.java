package com.grainwarehouse.employee.dto;

import com.grainwarehouse.common.search.HasActive;
import com.grainwarehouse.common.search.HasKeyword;

/**
 * Tiêu chí lọc danh sách nhân viên (GET /api/employees?keyword=&active=).
 * keyword khớp mã, tên hoặc SĐT. Mọi trường tùy chọn: null = không lọc.
 */
public record EmployeeSearchCriteria(String keyword, Boolean active) implements HasKeyword, HasActive {
}
