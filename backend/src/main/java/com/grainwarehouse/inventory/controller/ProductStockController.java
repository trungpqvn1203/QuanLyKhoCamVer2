package com.grainwarehouse.inventory.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.inventory.dto.ProductStockDetailResponse;
import com.grainwarehouse.inventory.dto.ProductStockResponse;
import com.grainwarehouse.inventory.dto.ProductStockSearchCriteria;
import com.grainwarehouse.inventory.service.ProductStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tồn kho theo sản phẩm — chỉ đọc. Thêm/sửa SP vẫn qua /api/products; tồn chỉ đổi qua chứng từ kho.
 * Đặt ở module inventory (không phải product) để không tạo phụ thuộc vòng product ↔ inventory.
 */
@RestController
@RequestMapping("/api/inventory/products")
@RequiredArgsConstructor
public class ProductStockController {

    private final ProductStockService productStockService;

    // sort nhận thuộc tính của Product (code, name...); chưa hỗ trợ sắp theo số tồn
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductStockResponse>>> list(
            @ModelAttribute ProductStockSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(productStockService.list(criteria, pageable)));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductStockDetailResponse>> get(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.ok(productStockService.get(productId)));
    }
}
