package com.grainwarehouse.inbound.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.inbound.dto.SupplierDebtResponse;
import com.grainwarehouse.inbound.service.SupplierDebtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Công nợ nhà cung cấp (BR-SPAY-01): GET /api/supplier-debts — mọi NCC mình đang còn nợ, nợ nhiều nhất lên đầu.
 * Không phân trang: chỉ gồm NCC còn nợ, số lượng nhỏ. ?supplierId= để xem một NCC.
 */
@RestController
@RequestMapping("/api/supplier-debts")
@RequiredArgsConstructor
public class SupplierDebtController {

    private final SupplierDebtService supplierDebtService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SupplierDebtResponse>>> list(
            @RequestParam(required = false) Long supplierId) {
        return ResponseEntity.ok(ApiResponse.ok(supplierDebtService.list(supplierId)));
    }
}
