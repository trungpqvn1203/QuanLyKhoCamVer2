package com.grainwarehouse.opening.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.opening.dto.CancelOpeningStockRequest;
import com.grainwarehouse.opening.dto.OpeningStockRequest;
import com.grainwarehouse.opening.dto.OpeningStockResponse;
import com.grainwarehouse.opening.dto.OpeningStockSearchCriteria;
import com.grainwarehouse.opening.dto.OpeningStockSummaryResponse;
import com.grainwarehouse.opening.service.OpeningStockService;
import com.grainwarehouse.sales.dto.ReasonRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phiếu tồn đầu kỳ (BR-OPN-01..10). Chỉ Quản lý hệ thống (SecurityConfig). Không có DELETE: nháp thì hủy,
 * đã xác nhận thì đảo. authentication.getName() = tên đăng nhập, ghi vào người lập / người xác nhận.
 */
@RestController
@RequestMapping("/api/opening-stocks")
@RequiredArgsConstructor
public class OpeningStockController {

    private final OpeningStockService openingStockService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OpeningStockSummaryResponse>>> list(
            @ModelAttribute OpeningStockSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(openingStockService.list(criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OpeningStockResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(openingStockService.get(id)));
    }

    // SP đã có nhập/xuất tại kho → giao diện làm mờ, không cho chọn (BR-OPN-05)
    @GetMapping("/products-with-movement")
    public ResponseEntity<ApiResponse<List<Long>>> productsWithMovement(@RequestParam Long warehouseId) {
        return ResponseEntity.ok(ApiResponse.ok(openingStockService.productsWithMovement(warehouseId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OpeningStockResponse>> create(
            @Valid @RequestBody OpeningStockRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                openingStockService.create(request, authentication.getName()), "Tạo phiếu tồn đầu kỳ nháp thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OpeningStockResponse>> update(
            @PathVariable Long id, @Valid @RequestBody OpeningStockRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(openingStockService.update(id, request),
                "Cập nhật phiếu tồn đầu kỳ thành công"));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<OpeningStockResponse>> complete(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(openingStockService.complete(id, authentication.getName()),
                "Xác nhận tồn đầu kỳ thành công"));
    }

    // Body tùy chọn: hủy nháp không cần lý do
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OpeningStockResponse>> cancel(
            @PathVariable Long id, @Valid @RequestBody(required = false) CancelOpeningStockRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(ApiResponse.ok(openingStockService.cancel(id, reason),
                "Hủy phiếu tồn đầu kỳ thành công"));
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<ApiResponse<OpeningStockResponse>> reverse(
            @PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(openingStockService.reverse(id, request.reason()),
                "Đảo phiếu tồn đầu kỳ thành công"));
    }
}
