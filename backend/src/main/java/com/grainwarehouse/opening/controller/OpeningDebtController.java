package com.grainwarehouse.opening.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.opening.dto.OpeningDebtParty;
import com.grainwarehouse.opening.dto.OpeningDebtRequest;
import com.grainwarehouse.opening.dto.OpeningDebtResponse;
import com.grainwarehouse.opening.dto.OpeningDebtSearchCriteria;
import com.grainwarehouse.opening.service.OpeningDebtService;
import com.grainwarehouse.sales.dto.ReasonRequest;
import com.grainwarehouse.sales.dto.RecordPaymentRequest;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * Công nợ đầu kỳ (BR-ODB-01..09) — cùng bộ endpoint cho khách và NCC, mỗi loại một lớp con với URL riêng
 * (/api/opening-customer-debts, /api/opening-supplier-debts) để SecurityConfig phân quyền theo URL như các module khác.
 * Không có PUT / DELETE: một khoản nợ chỉ là một con số, sai thì đảo rồi nhập lại.
 */
public abstract class OpeningDebtController {

    private final OpeningDebtService openingDebtService;
    private final OpeningDebtParty party;

    protected OpeningDebtController(OpeningDebtService openingDebtService, OpeningDebtParty party) {
        this.openingDebtService = openingDebtService;
        this.party = party;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OpeningDebtResponse>>> list(
            @ModelAttribute OpeningDebtSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(openingDebtService.list(party, criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OpeningDebtResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(openingDebtService.get(party, id)));
    }

    // Ghi nhận theo lô: mỗi dòng một chứng từ (BR-ODB-04)
    @PostMapping
    public ResponseEntity<ApiResponse<List<OpeningDebtResponse>>> create(
            @Valid @RequestBody OpeningDebtRequest request, Authentication authentication) {
        List<OpeningDebtResponse> created = openingDebtService.create(party, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created,
                "Đã ghi nhận " + created.size() + " khoản nợ đầu kỳ"));
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<ApiResponse<OpeningDebtResponse>> reverse(
            @PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(openingDebtService.reverse(party, id, request.reason()),
                "Đảo khoản nợ đầu kỳ thành công"));
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<ApiResponse<OpeningDebtResponse>> recordPayment(
            @PathVariable Long id, @Valid @RequestBody RecordPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                openingDebtService.recordPayment(party, id, request), "Ghi nhận thanh toán thành công"));
    }

    @PostMapping("/{id}/payments/{paymentId}/reverse")
    public ResponseEntity<ApiResponse<OpeningDebtResponse>> reversePayment(
            @PathVariable Long id, @PathVariable Long paymentId, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(openingDebtService.reversePayment(party, id, paymentId, request.reason()),
                "Hoàn khoản thanh toán thành công"));
    }
}
