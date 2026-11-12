package com.grainwarehouse.transfer.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.sales.dto.ReasonRequest;
import com.grainwarehouse.transfer.dto.CancelTransferRequest;
import com.grainwarehouse.transfer.dto.TransferRequest;
import com.grainwarehouse.transfer.dto.TransferResponse;
import com.grainwarehouse.transfer.dto.TransferSearchCriteria;
import com.grainwarehouse.transfer.dto.TransferSummaryResponse;
import com.grainwarehouse.transfer.service.TransferService;
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
 * Phiếu điều chuyển kho (BR-TRF-01..12). Quản lý kho + Quản lý hệ thống (SecurityConfig, BR-TRF-11).
 * Không có DELETE: nháp thì hủy, đã hoàn tất thì đảo. authentication.getName() = tên đăng nhập.
 */
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TransferSummaryResponse>>> list(
            @ModelAttribute TransferSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.list(criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransferResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> create(
            @Valid @RequestBody TransferRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                transferService.create(request, authentication.getName()), "Tạo phiếu điều chuyển nháp thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TransferResponse>> update(
            @PathVariable Long id, @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.update(id, request),
                "Cập nhật phiếu điều chuyển thành công"));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<TransferResponse>> complete(
            @PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.complete(id, authentication.getName()),
                "Hoàn tất điều chuyển thành công"));
    }

    // Body tùy chọn: hủy nháp không cần lý do
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<TransferResponse>> cancel(
            @PathVariable Long id, @Valid @RequestBody(required = false) CancelTransferRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(ApiResponse.ok(transferService.cancel(id, reason),
                "Hủy phiếu điều chuyển thành công"));
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<ApiResponse<TransferResponse>> reverse(
            @PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(transferService.reverse(id, request.reason()),
                "Đảo phiếu điều chuyển thành công"));
    }
}
