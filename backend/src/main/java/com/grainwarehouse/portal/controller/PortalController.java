package com.grainwarehouse.portal.controller;

import com.grainwarehouse.auth.dto.ChangePasswordRequest;
import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.portal.dto.PortalCancelRequest;
import com.grainwarehouse.portal.dto.PortalMeResponse;
import com.grainwarehouse.portal.dto.PortalOrderRequest;
import com.grainwarehouse.portal.dto.PortalOrderResponse;
import com.grainwarehouse.portal.dto.PortalOrderSummaryResponse;
import com.grainwarehouse.portal.dto.PortalProductResponse;
import com.grainwarehouse.portal.service.PortalAuthService;
import com.grainwarehouse.portal.service.PortalOrderService;
import com.grainwarehouse.portal.web.PortalCookies;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API của app khách (BR-POR-03, 06..09). Mọi hàm lấy tài khoản từ token (`sub`) — không có tham số customerId nào:
 * khách không thể "hỏi hộ" đơn của người khác bằng cách đổi số trên URL.
 */
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
public class PortalController {

    private final PortalOrderService portalOrderService;
    private final PortalAuthService portalAuthService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PortalMeResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(portalOrderService.me(accountId(jwt))));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @CookieValue(name = PortalCookies.REFRESH_TOKEN, required = false) String refreshToken,
            @Valid @RequestBody ChangePasswordRequest request) {
        portalAuthService.changePassword(accountId(jwt), refreshToken, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<PortalProductResponse>>> products() {
        return ResponseEntity.ok(ApiResponse.ok(portalOrderService.products()));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PageResponse<PortalOrderSummaryResponse>>> orders(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) StockDocumentStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(portalOrderService.list(accountId(jwt), status, pageable)));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<PortalOrderResponse>> order(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(portalOrderService.get(accountId(jwt), id)));
    }

    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<PortalOrderResponse>> createOrder(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PortalOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                portalOrderService.create(accountId(jwt), request), "Đã gửi đơn — cửa hàng sẽ xác nhận sớm"));
    }

    @PutMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<PortalOrderResponse>> updateOrder(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody PortalOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(portalOrderService.update(accountId(jwt), id, request),
                "Đã cập nhật đơn"));
    }

    @PostMapping("/orders/{id}/cancel")
    public ResponseEntity<ApiResponse<PortalOrderResponse>> cancelOrder(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody(required = false) PortalCancelRequest request) {
        String note = request == null ? null : request.note();
        return ResponseEntity.ok(ApiResponse.ok(portalOrderService.cancel(accountId(jwt), id, note), "Đã hủy đơn"));
    }

    private static Long accountId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
