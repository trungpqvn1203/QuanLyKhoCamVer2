package com.grainwarehouse.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT = cập nhật toàn bộ; có active để mở lại đơn vị đã ngừng dùng. */
public record UpdateProductUnitRequest(
        @NotBlank(message = "Tên đơn vị không được để trống")
        @Size(max = 50, message = "Tên đơn vị tối đa 50 ký tự")
        String name,

        @NotNull(message = "Trạng thái không được để trống")
        Boolean active) {
}
