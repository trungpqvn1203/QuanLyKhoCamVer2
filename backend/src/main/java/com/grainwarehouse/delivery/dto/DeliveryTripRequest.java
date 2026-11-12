package com.grainwarehouse.delivery.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Lập / sửa chuyến Đang xếp (BR-DLV-01..05). documentIds = phiếu bán theo ĐÚNG thứ tự giao: phần tử đầu là điểm
 * giao số 1. Sửa chuyến gửi lại toàn bộ danh sách (phiếu không còn trong danh sách = bỏ khỏi chuyến).
 * Danh sách rỗng được phép khi đang xếp; xuất chuyến mới bắt buộc có ít nhất một phiếu (BR-DLV-06).
 */
public record DeliveryTripRequest(
        @NotNull(message = "Chọn ngày đi giao")
        LocalDate tripDate,

        @NotNull(message = "Chọn xe")
        Long vehicleId,

        @NotNull(message = "Chọn lái xe")
        Long driverId,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note,

        @NotNull(message = "Danh sách phiếu giao không được để trống")
        @Size(max = 100, message = "Một chuyến tối đa 100 điểm giao")
        List<@NotNull(message = "Mã phiếu không được để trống") Long> documentIds) {
}
