package com.grainwarehouse.inbound.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.inbound.dto.CancelInboundReceiptRequest;
import com.grainwarehouse.inbound.dto.InboundReceiptRequest;
import com.grainwarehouse.inbound.dto.InboundReceiptResponse;
import com.grainwarehouse.inbound.dto.InboundReceiptSearchCriteria;
import com.grainwarehouse.inbound.dto.InboundReceiptSummaryResponse;
import com.grainwarehouse.inbound.dto.ReverseInboundReceiptRequest;
import com.grainwarehouse.inbound.service.InboundReceiptService;
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
 * Phiếu nhập kho. Đổi trạng thái bằng POST tới hành động riêng (complete/cancel/reverse), không cho
 * client PUT thẳng status — mỗi hành động có quy tắc và tác động tồn khác nhau.
 * Không có DELETE (BR-IMP-13): gọi DELETE → 405 METHOD_NOT_ALLOWED.
 */
@RestController
@RequestMapping("/api/inbound-receipts")
@RequiredArgsConstructor
public class InboundReceiptController {

    private final InboundReceiptService inboundReceiptService;

    // Mặc định phiếu mới nhất lên đầu
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<InboundReceiptSummaryResponse>>> list(
            @ModelAttribute InboundReceiptSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(inboundReceiptService.list(criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(inboundReceiptService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> create(
            @Valid @RequestBody InboundReceiptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(inboundReceiptService.create(request), "Tạo phiếu nhập nháp thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> update(
            @PathVariable Long id, @Valid @RequestBody InboundReceiptRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(inboundReceiptService.update(id, request),
                "Cập nhật phiếu nhập thành công"));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> complete(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(inboundReceiptService.complete(id),
                "Hoàn tất phiếu nhập thành công"));
    }

    // Body tùy chọn: hủy không cần lý do
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> cancel(
            @PathVariable Long id, @Valid @RequestBody(required = false) CancelInboundReceiptRequest request) {
        String reason = request == null ? null : request.reason();
        return ResponseEntity.ok(ApiResponse.ok(inboundReceiptService.cancel(id, reason),
                "Hủy phiếu nhập thành công"));
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<ApiResponse<InboundReceiptResponse>> reverse(
            @PathVariable Long id, @Valid @RequestBody ReverseInboundReceiptRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(inboundReceiptService.reverse(id, request.reason()),
                "Đảo phiếu nhập thành công"));
    }
}
