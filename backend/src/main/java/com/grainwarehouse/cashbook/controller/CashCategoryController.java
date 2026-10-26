package com.grainwarehouse.cashbook.controller;

import com.grainwarehouse.cashbook.dto.CashCategoryRequest;
import com.grainwarehouse.cashbook.dto.CashCategoryResponse;
import com.grainwarehouse.cashbook.dto.UpdateCashCategoryRequest;
import com.grainwarehouse.cashbook.service.CashCategoryService;
import com.grainwarehouse.common.response.ApiResponse;
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

/** Danh mục thu/chi khác (BR-CASH-05). Không có DELETE: ngừng dùng bằng PUT active=false. */
@RestController
@RequestMapping("/api/cash-categories")
@RequiredArgsConstructor
public class CashCategoryController {

    private final CashCategoryService cashCategoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CashCategoryResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(cashCategoryService.list()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CashCategoryResponse>> create(@Valid @RequestBody CashCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(cashCategoryService.create(request), "Tạo danh mục thu/chi thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CashCategoryResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateCashCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(cashCategoryService.update(id, request), "Cập nhật danh mục thành công"));
    }
}
