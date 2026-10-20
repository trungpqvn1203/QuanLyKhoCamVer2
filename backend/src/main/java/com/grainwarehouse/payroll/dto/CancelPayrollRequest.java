package com.grainwarehouse.payroll.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** BR-PAY-03: hủy chốt bắt buộc có lý do. */
public record CancelPayrollRequest(
        @NotBlank(message = "Nhập lý do hủy chốt")
        @Size(max = 500, message = "Lý do tối đa 500 ký tự")
        String reason) {
}
