package com.grainwarehouse.attendance.dto;

import com.grainwarehouse.attendance.entity.DayStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Ghi một ô bảng công (PUT = ghi đè cả ô). unloadTons / deliveryTons không gửi = 0. */
public record AttendanceRequest(
        @NotNull(message = "Chọn trạng thái ngày công")
        DayStatus status,

        @PositiveOrZero(message = "Số tấn bốc xuống không được âm")
        @Digits(integer = 5, fraction = 3, message = "Số tấn bốc xuống tối đa 5 chữ số phần nguyên và 3 chữ số thập phân")
        BigDecimal unloadTons,

        @PositiveOrZero(message = "Số tấn giao hàng không được âm")
        @Digits(integer = 5, fraction = 3, message = "Số tấn giao hàng tối đa 5 chữ số phần nguyên và 3 chữ số thập phân")
        BigDecimal deliveryTons,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note) {

    public BigDecimal effectiveUnloadTons() {
        return unloadTons == null ? BigDecimal.ZERO : unloadTons;
    }

    public BigDecimal effectiveDeliveryTons() {
        return deliveryTons == null ? BigDecimal.ZERO : deliveryTons;
    }
}
