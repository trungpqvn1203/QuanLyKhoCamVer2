package com.grainwarehouse.attendance.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Bảng công một tháng: mỗi nhân viên một dòng, kèm các ô đã chấm và tổng.
 * today: ngày hôm nay giờ VN — frontend khóa các ô tương lai mà không phải tự đoán múi giờ.
 */
public record MonthlyAttendanceResponse(
        YearMonth month,
        int daysInMonth,
        LocalDate today,
        List<Row> employees) {

    /**
     * paysByDay / paysByTon: nhân viên có lương ngày / đơn giá tấn không (BR-EMP-02) — để giao diện làm mờ phần
     * không tính tiền, không lộ đơn giá ra bảng công.
     */
    public record Row(
            Long employeeId,
            String employeeCode,
            String employeeName,
            boolean active,
            boolean paysByDay,
            boolean paysByTon,
            BigDecimal totalWorkUnits,
            BigDecimal totalTons,
            List<AttendanceDay> days) {
    }
}
