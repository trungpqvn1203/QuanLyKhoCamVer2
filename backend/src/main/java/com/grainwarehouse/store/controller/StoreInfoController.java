package com.grainwarehouse.store.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.store.dto.StoreInfoRequest;
import com.grainwarehouse.store.dto.StoreInfoResponse;
import com.grainwarehouse.store.service.StoreInfoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Đọc: mọi vai trò (ai in phiếu cũng cần); ghi: chỉ Quản lý hệ thống (SecurityConfig). */
@RestController
@RequestMapping("/api/store-info")
@RequiredArgsConstructor
public class StoreInfoController {

    private final StoreInfoService storeInfoService;

    @GetMapping
    public ResponseEntity<ApiResponse<StoreInfoResponse>> get() {
        return ResponseEntity.ok(ApiResponse.ok(storeInfoService.get()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<StoreInfoResponse>> save(@Valid @RequestBody StoreInfoRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(storeInfoService.save(request), "Đã lưu thông tin cửa hàng"));
    }
}
