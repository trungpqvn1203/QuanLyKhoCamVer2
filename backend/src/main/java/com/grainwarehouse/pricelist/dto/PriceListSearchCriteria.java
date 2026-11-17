package com.grainwarehouse.pricelist.dto;

import com.grainwarehouse.common.search.HasActive;
import com.grainwarehouse.common.search.HasKeyword;

/** Tiêu chí lọc danh sách bảng giá bán (GET /api/price-lists?keyword=&active=). */
public record PriceListSearchCriteria(String keyword, Boolean active) implements HasKeyword, HasActive {
}
