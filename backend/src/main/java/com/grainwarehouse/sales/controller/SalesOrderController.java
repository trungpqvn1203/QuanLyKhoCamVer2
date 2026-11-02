package com.grainwarehouse.sales.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.sales.dto.CancelSalesOrderRequest;
import com.grainwarehouse.sales.dto.PendingOrderRequest;
import com.grainwarehouse.sales.dto.PriceAdjustmentRequest;
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
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<ApiResponse<SalesOrderResponse>> create(
            @Valid @RequestBody SalesOrderRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                salesOrderService.create(request, authentication.getName()), "Tạo phiếu bán nháp thành công"));
    }

    /** BR-SAL-17: ghi đơn khách đặt — phiếu Chờ xác nhận, chưa có kho và giá. */
    @PostMapping("/pending")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> createPending(
            @Valid @RequestBody PendingOrderRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                salesOrderService.createPending(request, authentication.getName()), "Ghi đơn khách đặt thành công"));
    }

    /** BR-SAL-18: sửa phần khách đặt — chỉ khi Chờ xác nhận. */
    @PutMapping("/{id}/request")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> updatePending(
            @PathVariable Long id, @Valid @RequestBody PendingOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.updatePending(id, request),
                "Cập nhật đơn khách đặt thành công"));
    }

    /** BR-SAL-19: Chờ xác nhận → Nháp, body như phiếu nháp (kho, ngày bán, dòng hàng có giá). */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> confirm(
            @PathVariable Long id, @Valid @RequestBody SalesOrderRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.confirm(id, request, authentication.getName()),
                "Xác nhận đơn khách đặt thành công"));
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

    /** BR-SAL-23..26: đổi đơn giá phiếu Hoàn tất chưa thu đồng nào; lý do bắt buộc, ghi nhật ký bất biến. */
    @PostMapping("/{id}/price-adjustments")
    public ResponseEntity<ApiResponse<SalesOrderResponse>> adjustPrices(
            @PathVariable Long id, @Valid @RequestBody PriceAdjustmentRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(salesOrderService.adjustPrices(id, request, authentication.getName()),
                "Điều chỉnh giá thành công"));
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
