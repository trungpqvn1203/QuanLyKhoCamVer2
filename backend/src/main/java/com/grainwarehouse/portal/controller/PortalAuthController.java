package com.grainwarehouse.portal.controller;

import com.grainwarehouse.auth.service.AuthService.SessionTokens;
import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.portal.dto.PortalLoginRequest;
import com.grainwarehouse.portal.dto.PortalMeResponse;
import com.grainwarehouse.portal.service.PortalAuthService;
import com.grainwarehouse.portal.service.PortalAuthService.RefreshResult;
import com.grainwarehouse.portal.web.PortalCookies;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đăng nhập / gia hạn / đăng xuất app khách (ADR-0005). Như AuthController: token chỉ nằm trong cookie HttpOnly
 * (GW_CAT / GW_CRT), không bao giờ trong body. CSRF lấy qua GET /api/auth/csrf như trang nhân viên.
 */
@RestController
@RequestMapping("/api/portal/auth")
@RequiredArgsConstructor
public class PortalAuthController {

    private final PortalAuthService portalAuthService;
    private final PortalCookies portalCookies;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<PortalMeResponse>> login(
            @Valid @RequestBody PortalLoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletResponse response) {
        PortalAuthService.LoginResult result = portalAuthService.login(request, userAgent);
        setCookies(response, result.tokens());
        return ResponseEntity.ok(ApiResponse.ok(result.me(), "Đăng nhập thành công"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<PortalMeResponse>> refresh(
            @CookieValue(name = PortalCookies.REFRESH_TOKEN, required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletResponse response) {
        return switch (portalAuthService.refresh(refreshToken, userAgent)) {
            case RefreshResult.Renewed renewed -> {
                setCookies(response, renewed.tokens());
                yield ResponseEntity.ok(ApiResponse.ok(renewed.me()));
            }
            case RefreshResult.Race race -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                    "REFRESH_RACE", "Phiên vừa được gia hạn ở tab khác, gửi lại yêu cầu"));
            case RefreshResult.Rejected rejected -> {
                portalCookies.clear(response);
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                        "UNAUTHORIZED", "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"));
            }
        };
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = PortalCookies.REFRESH_TOKEN, required = false) String refreshToken,
            HttpServletResponse response) {
        portalAuthService.logout(refreshToken);
        portalCookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    private void setCookies(HttpServletResponse response, SessionTokens tokens) {
        portalCookies.setTokens(response, tokens.accessToken(), tokens.accessMaxAge(),
                tokens.refreshToken(), tokens.refreshMaxAge());
    }
}
