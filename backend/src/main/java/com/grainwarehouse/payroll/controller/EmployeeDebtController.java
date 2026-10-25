package com.grainwarehouse.payroll.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.payroll.dto.EmployeeDebtResponse;
import com.grainwarehouse.payroll.service.EmployeeDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Công nợ nhân viên (BR-EPAY-01, 03): GET /api/employee-debts — nhân viên đang còn lương chưa trả hoặc còn dư tạm ứng,
 * lương chưa trả nhiều nhất lên đầu. Không phân trang: chỉ gồm người còn nợ, số lượng nhỏ. ?employeeId= để xem một người.
 */
@RestController
@RequestMapping("/api/employee-debts")
@RequiredArgsConstructor
public class EmployeeDebtController {

    private final EmployeeDebtService employeeDebtService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<EmployeeDebtResponse>>> list(
            @RequestParam(required = false) Long employeeId) {
        return ResponseEntity.ok(ApiResponse.ok(employeeDebtService.list(employeeId)));
    }
}
