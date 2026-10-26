package com.grainwarehouse.cashbook.dto;

import com.grainwarehouse.cashbook.entity.CashDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Tạo danh mục thu/chi (BR-CASH-05). Chiều cố định từ lúc tạo — không có trong request sửa. */
public record CashCategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
        String name,

        @NotNull(message = "Chọn danh mục thu hay chi")
        CashDirection direction) {
}
