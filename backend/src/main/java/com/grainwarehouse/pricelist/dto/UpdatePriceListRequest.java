package com.grainwarehouse.pricelist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PUT = cập nhật toàn bộ; active=false để ngừng dùng, true để mở lại (BR-PRC-03). */
public record UpdatePriceListRequest(
        @NotBlank(message = "Tên bảng giá không được để trống")
        @Size(max = 100, message = "Tên bảng giá tối đa 100 ký tự")
        String name,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,

        @NotNull(message = "Trạng thái không được để trống")
        Boolean active) {
}
