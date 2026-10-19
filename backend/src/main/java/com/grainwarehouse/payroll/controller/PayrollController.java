package com.grainwarehouse.payroll.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.payroll.dto.CancelPayrollRequest;
import com.grainwarehouse.payroll.dto.PayrollSheetResponse;
import com.grainwarehouse.payroll.service.PayrollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/** Bảng lương theo tháng: /api/payrolls/2026-10. Tháng là định danh tự nhiên — mỗi tháng một bảng còn hiệu lực. */
@RestController
@RequestMapping("/api/payrolls/{month}")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;

    @GetMapping
    public ResponseEntity<ApiResponse<PayrollSheetResponse>> sheet(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(ApiResponse.ok(payrollService.sheet(month)));
    }

    @PostMapping("/finalize")
    public ResponseEntity<ApiResponse<PayrollSheetResponse>> finalizeMonth(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(ApiResponse.ok(payrollService.finalizeMonth(month), "Đã chốt lương"));
    }

    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<PayrollSheetResponse>> cancel(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @Valid @RequestBody CancelPayrollRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(payrollService.cancel(month, request.reason()), "Đã hủy chốt lương"));
    }
}
