package com.grainwarehouse.stockcount.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.opening.dto.CancelOpeningStockRequest;
import com.grainwarehouse.sales.dto.ReasonRequest;
import com.grainwarehouse.stockcount.dto.StockCountCreateRequest;
import com.grainwarehouse.stockcount.dto.StockCountResponse;
import com.grainwarehouse.stockcount.dto.StockCountSearchCriteria;
import com.grainwarehouse.stockcount.dto.StockCountSummaryResponse;
import com.grainwarehouse.stockcount.dto.StockCountUpdateRequest;
import com.grainwarehouse.stockcount.service.StockCountService;
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
 * Phiếu kiểm kê (BR-CNT-01..10). Chỉ Quản lý hệ thống (SecurityConfig). Không có DELETE: nháp thì hủy, đã xác nhận
 * thì đảo. authentication.getName() = tên đăng nhập, ghi vào người lập / người xác nhận.
 */
@RestController
@RequestMapping("/api/stock-counts")
@RequiredArgsConstructor
public class StockCountController {

    private final StockCountService stockCountService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StockCountSummaryResponse>>> list(
            @ModelAttribute StockCountSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(stockCountService.list(criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StockCountResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(stockCountService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StockCountResponse>> create(
            @Valid @RequestBody StockCountCreateRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                stockCountService.create(request, authentication.getName()), "Lập phiếu kiểm kê thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StockCountResponse>> update(
            @PathVariable Long id, @Valid @RequestBody StockCountUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(stockCountService.update(id, request), "Lưu phiếu kiểm kê thành công"));
    }

    // Chụp lại SL sổ sách theo tồn hiện tại (BR-CNT-03)
    @PostMapping("/{id}/refresh-book")
    public ResponseEntity<ApiResponse<StockCountResponse>> refreshBook(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(stockCountService.refreshBook(id), "Đã lấy lại số sổ sách"));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<StockCountResponse>> complete(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(stockCountService.complete(id, authentication.getName()),
                "Xác nhận kiểm kê thành công"));
    }

    // Body tùy chọn: hủy nháp không cần lý do (cùng dạng body với hủy phiếu tồn đầu kỳ)
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<StockCountResponse>> cancel(
            @PathVariable Long id, @Valid @RequestBody(required = false) CancelOpeningStockRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(ApiResponse.ok(stockCountService.cancel(id, reason), "Hủy phiếu kiểm kê thành công"));
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<ApiResponse<StockCountResponse>> reverse(
            @PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(stockCountService.reverse(id, request.reason()),
                "Đảo phiếu kiểm kê thành công"));
    }
}
