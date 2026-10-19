package com.grainwarehouse.attendance.dto;

import com.grainwarehouse.attendance.entity.Attendance;
import com.grainwarehouse.attendance.entity.DayStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Một ô đã chấm. Ngày chưa chấm không có trong danh sách (khác với "Nghỉ" — đã chấm là nghỉ). */
public record AttendanceDay(
        Long employeeId,
        LocalDate date,
        DayStatus status,
        BigDecimal tons,
        String note) {

    public static AttendanceDay from(Attendance attendance) {
        return new AttendanceDay(
                attendance.getEmployee().getId(),
                attendance.getWorkDate(),
                attendance.getDayStatus(),
                attendance.getTons(),
                attendance.getNote());
    }
}
