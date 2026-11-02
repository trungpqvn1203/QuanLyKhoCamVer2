package com.grainwarehouse.portal.controller;

import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.portal.dto.PortalAccountResponse;
import com.grainwarehouse.portal.dto.PortalPasswordRequest;
import com.grainwarehouse.portal.service.PortalAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cửa hàng quản lý tài khoản app của khách (BR-POR-02, 10). Nằm dưới /api/customers/** của chuỗi NHÂN VIÊN →
 * dùng đúng quyền của khách hàng: ai cũng xem được, bán hàng + quản lý hệ thống mới ghi (SecurityConfig).
 */
@RestController
@RequestMapping("/api/customers/{customerId}/portal-account")
@RequiredArgsConstructor
public class CustomerPortalAccountController {

    private final PortalAccountService portalAccountService;

    @GetMapping
    public ResponseEntity<ApiResponse<PortalAccountResponse>> get(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.ok(portalAccountService.get(customerId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PortalAccountResponse>> create(
            @PathVariable Long customerId, @Valid @RequestBody PortalPasswordRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                portalAccountService.create(customerId, request.password(), authentication.getName()),
                "Đã tạo tài khoản app — báo khách đăng nhập bằng SĐT và mật khẩu này, khách sẽ phải đổi mật khẩu"));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<PortalAccountResponse>> resetPassword(
            @PathVariable Long customerId, @Valid @RequestBody PortalPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(portalAccountService.resetPassword(customerId, request.password()),
                "Đã đặt lại mật khẩu — khách phải đăng nhập lại và đổi mật khẩu"));
    }

    @PostMapping("/lock")
    public ResponseEntity<ApiResponse<PortalAccountResponse>> lock(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.ok(portalAccountService.lock(customerId), "Đã khóa tài khoản app"));
    }

    @PostMapping("/unlock")
    public ResponseEntity<ApiResponse<PortalAccountResponse>> unlock(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.ok(portalAccountService.unlock(customerId), "Đã mở khóa tài khoản app"));
    }
}
