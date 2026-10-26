package com.grainwarehouse.cashbook.controller;

import com.grainwarehouse.cashbook.dto.CashVoucherRequest;
import com.grainwarehouse.cashbook.dto.CashVoucherResponse;
import com.grainwarehouse.cashbook.dto.CashVoucherSearchCriteria;
import com.grainwarehouse.cashbook.service.CashVoucherService;
import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.sales.dto.ReasonRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phiếu thu / phiếu chi khác (BR-CASH-04). Không có PUT/DELETE: phiếu bất biến, ghi nhầm thì hoàn. */
@RestController
@RequestMapping("/api/cash-vouchers")
@RequiredArgsConstructor
public class CashVoucherController {

    private final CashVoucherService cashVoucherService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CashVoucherResponse>>> list(
            @ModelAttribute CashVoucherSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(cashVoucherService.list(criteria, pageable)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CashVoucherResponse>> create(@Valid @RequestBody CashVoucherRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(cashVoucherService.create(request), "Lập phiếu thành công"));
    }

    @PostMapping("/{id}/reverse")
    public ResponseEntity<ApiResponse<CashVoucherResponse>> reverse(
            @PathVariable Long id, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(cashVoucherService.reverse(id, request.reason()), "Đã hoàn phiếu"));
    }
}
