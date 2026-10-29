package com.grainwarehouse.report.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.report.dto.DashboardResponse;
import com.grainwarehouse.report.dto.StockByWarehouseResponse;
import com.grainwarehouse.report.dto.StockMovementResponse;
import com.grainwarehouse.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Báo cáo chỉ đọc (BR-RPT). Mọi vai trò xem được — tồn kho và phiếu bán vốn đã cho mọi vai trò xem
 * (SecurityConfig). Công nợ, sổ quỹ không nằm ở đây mà lấy từ API có phân quyền riêng.
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(reportService.dashboard()));
    }

    @GetMapping("/stock-by-warehouse")
    public ResponseEntity<ApiResponse<StockByWarehouseResponse>> stockByWarehouse(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "true") boolean hideZero) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.stockByWarehouse(categoryId, keyword, hideZero)));
    }

    @GetMapping("/stock-movement")
    public ResponseEntity<ApiResponse<StockMovementResponse>> stockMovement(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId) {
        return ResponseEntity.ok(ApiResponse.ok(reportService.stockMovement(from, to, warehouseId, categoryId)));
    }
}
