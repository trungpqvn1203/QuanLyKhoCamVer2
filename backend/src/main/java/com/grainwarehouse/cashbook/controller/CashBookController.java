package com.grainwarehouse.cashbook.controller;

import com.grainwarehouse.cashbook.dto.CashBookOpeningRequest;
import com.grainwarehouse.cashbook.dto.CashBookOpeningResponse;
import com.grainwarehouse.cashbook.dto.CashBookSummaryResponse;
import com.grainwarehouse.cashbook.dto.CashLedgerResponse;
import com.grainwarehouse.cashbook.entity.CashAccount;
import com.grainwarehouse.cashbook.entity.CashEntrySource;
import com.grainwarehouse.cashbook.service.CashBookService;
import com.grainwarehouse.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Sổ quỹ (BR-CASH-01..03, 06): /api/cash-book — chỉ quản lý hệ thống (BR-CASH-07). */
@RestController
@RequestMapping("/api/cash-book")
@RequiredArgsConstructor
public class CashBookController {

    private final CashBookService cashBookService;

    /** ?account=CASH|BANK (bắt buộc) &from=&to= (YYYY-MM-DD, mặc định đầu tháng → hôm nay) &source= (tùy chọn). */
    @GetMapping
    public ResponseEntity<ApiResponse<CashLedgerResponse>> ledger(
            @RequestParam CashAccount account,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) CashEntrySource source) {
        return ResponseEntity.ok(ApiResponse.ok(cashBookService.ledger(account, from, to, source)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<CashBookSummaryResponse>> summary() {
        return ResponseEntity.ok(ApiResponse.ok(cashBookService.summary()));
    }

    @GetMapping("/opening")
    public ResponseEntity<ApiResponse<CashBookOpeningResponse>> opening() {
        return ResponseEntity.ok(ApiResponse.ok(cashBookService.opening()));
    }

    /** authentication.getName() = tên đăng nhập (claim username, SecurityConfig) — ghi vào lịch sử sửa đầu kỳ. */
    @PutMapping("/opening")
    public ResponseEntity<ApiResponse<CashBookOpeningResponse>> setOpening(
            @Valid @RequestBody CashBookOpeningRequest request, Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.ok(cashBookService.setOpening(request, authentication.getName()),
                "Đã lưu đầu kỳ sổ quỹ"));
    }
}
