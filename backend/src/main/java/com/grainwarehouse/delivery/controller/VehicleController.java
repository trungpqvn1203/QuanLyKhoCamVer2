package com.grainwarehouse.delivery.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.delivery.dto.VehicleRequest;
import com.grainwarehouse.delivery.dto.VehicleResponse;
import com.grainwarehouse.delivery.dto.VehicleSearchCriteria;
import com.grainwarehouse.delivery.service.VehicleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Danh mục xe (BR-VEH-01..04). Quyền ghi: quản lý kho + quản lý hệ thống (SecurityConfig). */
@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<VehicleResponse>>> list(
            @ModelAttribute VehicleSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "plateNumber") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.list(criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VehicleResponse>> create(@Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(vehicleService.create(request), "Đã thêm xe"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VehicleResponse>> update(@PathVariable Long id,
                                                               @Valid @RequestBody VehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.update(id, request), "Đã cập nhật xe"));
    }

    // BR-VEH-04: ngừng dùng, không xóa cứng — 204 như các danh mục khác
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        vehicleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
