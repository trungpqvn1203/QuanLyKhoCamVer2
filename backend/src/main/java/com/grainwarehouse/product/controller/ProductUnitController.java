package com.grainwarehouse.product.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.product.dto.CreateProductUnitRequest;
import com.grainwarehouse.product.dto.ProductUnitResponse;
import com.grainwarehouse.product.dto.UpdateProductUnitRequest;
import com.grainwarehouse.product.service.ProductUnitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-units")
@RequiredArgsConstructor
public class ProductUnitController {

    private final ProductUnitService unitService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductUnitResponse>>> list(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(unitService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductUnitResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(unitService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductUnitResponse>> create(@Valid @RequestBody CreateProductUnitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(unitService.create(request), "Tạo đơn vị thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductUnitResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateProductUnitRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(unitService.update(id, request), "Cập nhật đơn vị thành công"));
    }

    // Soft delete (BR-UNIT-02): 204 No Content theo contract-spec
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        unitService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
