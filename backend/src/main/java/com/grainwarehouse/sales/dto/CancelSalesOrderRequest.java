package com.grainwarehouse.sales.dto;

import jakarta.validation.constraints.Size;

/** Lý do hủy: tùy chọn với phiếu nháp (chưa ảnh hưởng tồn), bắt buộc với đơn khách đặt (BR-SAL-21 — service kiểm tra). */
public record CancelSalesOrderRequest(
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason) {
}
