package com.grainwarehouse.delivery.dto;

import com.grainwarehouse.common.search.HasActive;
import com.grainwarehouse.common.search.HasKeyword;

/** GET /api/vehicles?keyword=&active= — null = không lọc. */
public record VehicleSearchCriteria(String keyword, Boolean active) implements HasKeyword, HasActive {
}
