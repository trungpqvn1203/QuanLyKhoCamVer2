package com.grainwarehouse.attendance.dto;

import com.grainwarehouse.attendance.entity.DayStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Ghi một ô bảng công (PUT = ghi đè cả ô). tons không gửi = 0. */
public record AttendanceRequest(
        @NotNull(message = "Chọn trạng thái ngày công")
        DayStatus status,

        @PositiveOrZero(message = "Số tấn không được âm")
        @Digits(integer = 5, fraction = 3, message = "Số tấn tối đa 5 chữ số phần nguyên và 3 chữ số thập phân")
        BigDecimal tons,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note) {

    public BigDecimal effectiveTons() {
        return tons == null ? BigDecimal.ZERO : tons;
    }
}
