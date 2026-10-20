package com.grainwarehouse.sales.dto;

import jakarta.validation.constraints.Size;

/** Lý do hủy phiếu bán nháp — tùy chọn (phiếu nháp chưa ảnh hưởng tồn). */
public record CancelSalesOrderRequest(
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason) {
}
