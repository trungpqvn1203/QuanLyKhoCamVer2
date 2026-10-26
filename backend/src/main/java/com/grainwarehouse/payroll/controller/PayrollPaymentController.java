package com.grainwarehouse.payroll.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.payroll.dto.PayrollItemPaymentsResponse;
import com.grainwarehouse.payroll.dto.PayrollPaymentRequest;
import com.grainwarehouse.payroll.service.PayrollPaymentService;
import com.grainwarehouse.sales.dto.ReasonRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * Trả lương theo dòng lương của bảng lương tháng đã chốt (BR-EPAY-02, 04):
 * /api/payrolls/2026-10/items/{employeeId}. Không đặt chung PayrollController vì khác nhóm nghiệp vụ (tiền chi).
 */
@RestController
@RequestMapping("/api/payrolls/{month}/items/{employeeId}")
@RequiredArgsConstructor
public class PayrollPaymentController {

    private final PayrollPaymentService payrollPaymentService;

    @GetMapping
    public ResponseEntity<ApiResponse<PayrollItemPaymentsResponse>> get(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, @PathVariable Long employeeId) {
        return ResponseEntity.ok(ApiResponse.ok(payrollPaymentService.get(month, employeeId)));
    }

    @PostMapping("/payments")
    public ResponseEntity<ApiResponse<PayrollItemPaymentsResponse>> pay(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, @PathVariable Long employeeId,
            @Valid @RequestBody PayrollPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(payrollPaymentService.pay(month, employeeId, request), "Ghi nhận trả lương thành công"));
    }

    @PostMapping("/payments/{paymentId}/reverse")
    public ResponseEntity<ApiResponse<PayrollItemPaymentsResponse>> reversePayment(
            @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth month, @PathVariable Long employeeId,
            @PathVariable Long paymentId, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                payrollPaymentService.reversePayment(month, employeeId, paymentId, request.reason()),
                "Hoàn khoản trả lương thành công"));
    }
}
