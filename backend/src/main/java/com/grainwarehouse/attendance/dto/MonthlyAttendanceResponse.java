package com.grainwarehouse.attendance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Bảng công một tháng: mỗi nhân viên một dòng, kèm các ô đã chấm và tổng.
 * today: ngày hôm nay giờ VN — frontend khóa các ô tương lai mà không phải tự đoán múi giờ.
 * payrollFinalized: tháng đã chốt lương → cả bảng công chỉ xem (BR-PAY-03).
 */
public record MonthlyAttendanceResponse(
        YearMonth month,
        int daysInMonth,
        LocalDate today,
        boolean payrollFinalized,
        List<Row> employees) {

    /**
     * paysByDay / paysByUnload / paysByDelivery: nhân viên có lương ngày / đơn giá tấn bốc xuống / đơn giá tấn giao
     * hàng không (BR-EMP-02, BR-EMP-04) — để giao diện làm mờ phần không tính tiền, không lộ đơn giá ra bảng công.
     */
    public record Row(
            Long employeeId,
            String employeeCode,
            String employeeName,
            boolean active,
            boolean paysByDay,
            boolean paysByUnload,
            boolean paysByDelivery,
            BigDecimal totalWorkUnits,
            BigDecimal totalUnloadTons,
            BigDecimal totalDeliveryTons,
            List<AttendanceDay> days) {
    }
}
