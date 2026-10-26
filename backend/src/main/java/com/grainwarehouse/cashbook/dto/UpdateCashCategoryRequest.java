package com.grainwarehouse.cashbook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT danh mục: đổi tên, ngừng dùng / mở lại. Không đổi chiều thu/chi (phiếu cũ sẽ mang nghĩa ngược lại). */
public record UpdateCashCategoryRequest(
        @NotBlank(message = "Tên danh mục không được để trống")
        @Size(max = 100, message = "Tên danh mục tối đa 100 ký tự")
        String name,

        @NotNull(message = "Trạng thái không được để trống")
        Boolean active) {
}
