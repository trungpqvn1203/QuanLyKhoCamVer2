package com.grainwarehouse.employee.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.employee.dto.EmployeeAdvanceLedgerResponse;
import com.grainwarehouse.employee.service.EmployeeAdvanceService;
import com.grainwarehouse.sales.dto.ReasonRequest;
import com.grainwarehouse.sales.dto.RecordPaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tạm ứng cho nhân viên (BR-EPAY-03, 04): /api/employees/{employeeId}/advances. Dùng lại RecordPaymentRequest
 * (ngày, tiền mặt, chuyển khoản, ghi chú) vì một lần ứng có đúng hình dạng đó; paymentDate ở đây là ngày ứng.
 */
@RestController
@RequestMapping("/api/employees/{employeeId}/advances")
@RequiredArgsConstructor
public class EmployeeAdvanceController {

    private final EmployeeAdvanceService employeeAdvanceService;

    @GetMapping
    public ResponseEntity<ApiResponse<EmployeeAdvanceLedgerResponse>> get(@PathVariable Long employeeId) {
        return ResponseEntity.ok(ApiResponse.ok(employeeAdvanceService.get(employeeId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeAdvanceLedgerResponse>> record(
            @PathVariable Long employeeId, @Valid @RequestBody RecordPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(employeeAdvanceService.record(employeeId, request), "Ghi nhận tạm ứng thành công"));
    }

    @PostMapping("/{advanceId}/reverse")
    public ResponseEntity<ApiResponse<EmployeeAdvanceLedgerResponse>> reverse(
            @PathVariable Long employeeId, @PathVariable Long advanceId, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                employeeAdvanceService.reverse(employeeId, advanceId, request.reason()), "Hoàn khoản ứng thành công"));
    }
}
