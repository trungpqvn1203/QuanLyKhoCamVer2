package com.grainwarehouse.delivery.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.delivery.dto.DeliveryOrderResponse;
import com.grainwarehouse.delivery.dto.DeliveryTripRequest;
import com.grainwarehouse.delivery.dto.DeliveryTripResponse;
import com.grainwarehouse.delivery.dto.DeliveryTripSearchCriteria;
import com.grainwarehouse.delivery.dto.DeliveryTripSummaryResponse;
import com.grainwarehouse.delivery.dto.DriverResponse;
import com.grainwarehouse.delivery.service.DeliveryTripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Chuyến giao hàng (BR-DLV-01..10). Ai đăng nhập cũng xem được; lập / sửa / xóa / xuất chuyến: quản lý kho + quản lý
 * hệ thống (SecurityConfig). Xuất chuyến hoàn tất phiếu bán dù quản lý kho không có quyền Hoàn tất ở màn phiếu bán —
 * ngoại lệ có chủ đích, chủ cửa hàng xác nhận 2026-11-03 (BR-DLV-10).
 */
@RestController
@RequestMapping("/api/delivery-trips")
@RequiredArgsConstructor
public class DeliveryTripController {

    private final DeliveryTripService tripService;

    // Mặc định: ngày đi giao mới nhất lên đầu, cùng ngày thì chuyến lập sau lên trước
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DeliveryTripSummaryResponse>>> list(
            @ModelAttribute DeliveryTripSearchCriteria criteria,
            @PageableDefault(size = 20)
            @SortDefault.SortDefaults({
                    @SortDefault(sort = "tripDate", direction = Sort.Direction.DESC),
                    @SortDefault(sort = "id", direction = Sort.Direction.DESC)}) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.list(criteria, pageable)));
    }

    /** Phiếu bán Nháp chưa xếp chuyến (BR-DLV-03). */
    @GetMapping("/assignable-orders")
    public ResponseEntity<ApiResponse<List<DeliveryOrderResponse>>> assignableOrders() {
        return ResponseEntity.ok(ApiResponse.ok(tripService.assignableOrders()));
    }

    @GetMapping("/drivers")
    public ResponseEntity<ApiResponse<List<DriverResponse>>> drivers() {
        return ResponseEntity.ok(ApiResponse.ok(tripService.drivers()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> create(
            @Valid @RequestBody DeliveryTripRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(tripService.create(request, authentication.getName()), "Đã lập chuyến giao"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> update(
            @PathVariable Long id, @Valid @RequestBody DeliveryTripRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.update(id, request), "Đã lưu chuyến giao"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tripService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dispatch")
    public ResponseEntity<ApiResponse<DeliveryTripResponse>> dispatch(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(tripService.dispatch(id, authentication.getName()),
                "Đã xuất chuyến — các phiếu bán đã hoàn tất và trừ tồn"));
    }
}
