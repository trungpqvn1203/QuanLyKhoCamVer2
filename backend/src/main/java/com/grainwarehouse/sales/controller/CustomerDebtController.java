package com.grainwarehouse.sales.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.sales.dto.CustomerDebtResponse;
import com.grainwarehouse.sales.service.CustomerDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Công nợ khách hàng (BR-SAL-10): GET /api/customer-debts — mọi khách đang còn nợ, nợ nhiều nhất lên đầu.
 * Không phân trang: chỉ gồm khách còn nợ, số lượng nhỏ. ?customerId= để xem một khách.
 */
@RestController
@RequestMapping("/api/customer-debts")
@RequiredArgsConstructor
public class CustomerDebtController {

    private final CustomerDebtService customerDebtService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CustomerDebtResponse>>> list(
            @RequestParam(required = false) Long customerId) {
        return ResponseEntity.ok(ApiResponse.ok(customerDebtService.list(customerId)));
    }
}
