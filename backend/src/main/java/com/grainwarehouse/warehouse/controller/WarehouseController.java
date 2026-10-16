package com.grainwarehouse.warehouse.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.warehouse.dto.CreateWarehouseRequest;
import com.grainwarehouse.warehouse.dto.UpdateWarehouseRequest;
import com.grainwarehouse.warehouse.dto.WarehouseResponse;
import com.grainwarehouse.warehouse.service.WarehouseService;
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
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WarehouseResponse>>> list(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.list(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WarehouseResponse>> create(@Valid @RequestBody CreateWarehouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(warehouseService.create(request), "Tạo kho thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WarehouseResponse>> update(@PathVariable Long id,
                                                                 @Valid @RequestBody UpdateWarehouseRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(warehouseService.update(id, request), "Cập nhật kho thành công"));
    }

    // Soft delete (BR-WH-04): 204 No Content theo contract-spec
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        warehouseService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
