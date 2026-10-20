package com.grainwarehouse.inventory.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.inventory.dto.StockTransactionResponse;
import com.grainwarehouse.inventory.dto.StockTransactionSearchCriteria;
import com.grainwarehouse.inventory.service.StockTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lịch sử biến động tồn kho (stock_transaction) — chỉ đọc, không có POST/PUT/DELETE (BR-INV-05). */
@RestController
@RequestMapping("/api/inventory/transactions")
@RequiredArgsConstructor
public class StockTransactionController {

    private final StockTransactionService stockTransactionService;

    /**
     * Mặc định mới nhất trước theo id — không theo createdAt: các giao dịch của một lần hoàn tất phiếu
     * dùng chung now() của transaction nên trùng createdAt; id luôn tăng đúng thứ tự ghi sổ.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<StockTransactionResponse>>> list(
            @ModelAttribute StockTransactionSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(stockTransactionService.list(criteria, pageable)));
    }
}
