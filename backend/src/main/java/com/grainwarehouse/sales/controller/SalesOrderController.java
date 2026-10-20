package com.grainwarehouse.sales.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.sales.dto.CancelSalesOrderRequest;
import com.grainwarehouse.sales.dto.ReasonRequest;
import com.grainwarehouse.sales.dto.RecordPaymentRequest;
import com.grainwarehouse.sales.dto.SalesOrderRequest;
import com.grainwarehouse.sales.dto.SalesOrderResponse;
import com.grainwarehouse.sales.dto.SalesOrderSearchCriteria;
import com.grainwarehouse.sales.dto.SalesOrderSummaryResponse;
import com.grainwarehouse.sales.service.SalesOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phiếu bán / xuất kho. Như phiếu nhập: đổi trạng thái bằng POST tới hành động riêng, không có DELETE.
 * Thanh toán là tài nguyên con của phiếu; mọi hành động trả lại chi tiết phiếu (kèm lịch sử thanh toán).
 */
@RestController
@RequestMapping("/api/sales-orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SalesOrderSummaryResponse>>> list(
            @ModelAttribute SalesOrderSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.list(criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SalesOrderResponse>> create(@Valid @RequestBody SalesOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(salesOrderService.create(request), "Tạo phiếu bán nháp thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> update(
            @PathVariable Long id, @Valid @RequestBody SalesOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.update(id, request),
                "Cập nhật phiếu bán thành công"));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> complete(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.complete(id), "Hoàn tất phiếu bán thành công"));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> cancel(
            @PathVariable Long id, @Valid @RequestBody(required = false) CancelSalesOrderRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.cancel(id, reason), "Hủy phiếu bán thành công"));
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> reverse(
            @PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.reverse(id, request.reason()),
                "Đảo phiếu bán thành công"));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> recordPayment(
            @PathVariable Long id, @Valid @RequestBody RecordPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(salesOrderService.recordPayment(id, request), "Ghi nhận thanh toán thành công"));
    }

    @PostMapping("/{id}/payments/{paymentId}/reverse")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> reversePayment(
            @PathVariable Long id, @PathVariable Long paymentId, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.reversePayment(id, paymentId, request.reason()),
                "Hoàn khoản thu thành công"));
    }
}
