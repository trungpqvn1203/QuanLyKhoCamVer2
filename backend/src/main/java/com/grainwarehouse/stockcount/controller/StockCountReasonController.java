package com.grainwarehouse.stockcount.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.stockcount.dto.StockCountReasonRequest;
import com.grainwarehouse.stockcount.dto.StockCountReasonResponse;
import com.grainwarehouse.stockcount.dto.UpdateStockCountReasonRequest;
import com.grainwarehouse.stockcount.service.StockCountReasonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Lý do lệch kiểm kê (BR-CNT-05). Không có DELETE: ngừng dùng bằng PUT active=false. */
@RestController
@RequestMapping("/api/stock-count-reasons")
@RequiredArgsConstructor
public class StockCountReasonController {

    private final StockCountReasonService stockCountReasonService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StockCountReasonResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(stockCountReasonService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StockCountReasonResponse>> create(
            @Valid @RequestBody StockCountReasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(stockCountReasonService.create(request), "Tạo lý do lệch thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<StockCountReasonResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateStockCountReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(stockCountReasonService.update(id, request),
                "Cập nhật lý do lệch thành công"));
    }
}
