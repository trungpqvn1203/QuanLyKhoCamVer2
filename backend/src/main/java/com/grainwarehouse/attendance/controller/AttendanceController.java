package com.grainwarehouse.attendance.controller;

import com.grainwarehouse.attendance.dto.AttendanceDay;
import com.grainwarehouse.attendance.dto.AttendanceRequest;
import com.grainwarehouse.attendance.dto.MonthlyAttendanceResponse;
import com.grainwarehouse.attendance.service.AttendanceService;
import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.search.CommonSpecifications;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;

@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    // ?month=2026-10; không gửi = tháng hiện tại theo giờ VN. Sai định dạng → 400 (MethodArgumentTypeMismatch)
    @GetMapping
    public ResponseEntity<ApiResponse<MonthlyAttendanceResponse>> monthly(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        YearMonth effective = month != null ? month : YearMonth.now(CommonSpecifications.BUSINESS_ZONE);
        return ResponseEntity.ok(ApiResponse.ok(attendanceService.monthly(effective)));
    }

    // PUT: ô là tài nguyên định danh bởi (nhân viên, ngày) — gọi lại bao nhiêu lần kết quả vẫn vậy
    @PutMapping("/{employeeId}/{date}")
    public ResponseEntity<ApiResponse<AttendanceDay>> record(
            @PathVariable Long employeeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody AttendanceRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(attendanceService.record(employeeId, date, request), "Đã chấm công"));
    }

    @DeleteMapping("/{employeeId}/{date}")
    public ResponseEntity<Void> clear(
            @PathVariable Long employeeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        attendanceService.clear(employeeId, date);
        return ResponseEntity.noContent().build();
    }
}
