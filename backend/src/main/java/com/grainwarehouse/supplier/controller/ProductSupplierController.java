package com.grainwarehouse.supplier.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.supplier.dto.ProductSupplierResponse;
import com.grainwarehouse.supplier.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * URL nằm dưới /api/products nhưng code đặt trong module supplier:
 * supplier đã phụ thuộc product (kiểm tra SP khi gán) → đặt ở product sẽ thành phụ thuộc vòng.
 * Gán/gỡ từ phía SP dùng lại POST/DELETE /api/suppliers/{id}/products — không viết trùng logic.
 */
@RestController
@RequestMapping("/api/products/{productId}/suppliers")
@RequiredArgsConstructor
public class ProductSupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductSupplierResponse>>> list(
            @PathVariable Long productId,
            @PageableDefault(size = 20, sort = "supplier.code") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.listSuppliersOfProduct(productId, pageable)));
    }
}
