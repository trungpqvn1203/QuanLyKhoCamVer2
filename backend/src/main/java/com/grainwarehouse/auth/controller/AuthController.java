package com.grainwarehouse.auth.controller;

import com.grainwarehouse.auth.dto.ChangePasswordRequest;
import com.grainwarehouse.auth.dto.CurrentUserResponse;
import com.grainwarehouse.auth.dto.LoginRequest;
import com.grainwarehouse.auth.service.AuthService;
import com.grainwarehouse.auth.service.AuthService.RefreshResult;
import com.grainwarehouse.auth.service.AuthService.SessionTokens;
import com.grainwarehouse.auth.web.AuthCookies;
import com.grainwarehouse.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đăng nhập / gia hạn / đăng xuất (ADR-0003 mục 7). Token không bao giờ nằm trong body JSON — chỉ trong cookie
 * HttpOnly, để JavaScript của trang không có cách nào đọc hay lưu nó.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookies authCookies;

    /** Không làm gì: CsrfFilter đã đặt cookie XSRF-TOKEN trên response. Trang đăng nhập gọi trước khi POST. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request, userAgent);
        setCookies(response, result.tokens());
        return ResponseEntity.ok(ApiResponse.ok(result.user(), "Đăng nhập thành công"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> refresh(
            @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
            HttpServletResponse response) {
        return switch (authService.refresh(refreshToken, userAgent)) {
            case RefreshResult.Renewed renewed -> {
                setCookies(response, renewed.tokens());
                yield ResponseEntity.ok(ApiResponse.ok(renewed.user()));
            }
            // Không đụng cookie: tab khác vừa nhận cookie mới, trình duyệt dùng chung cookie cho mọi tab
            case RefreshResult.Race race -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                    "REFRESH_RACE", "Phiên vừa được gia hạn ở tab khác, gửi lại yêu cầu"));
            case RefreshResult.Rejected rejected -> {
                authCookies.clear(response);
                yield ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                        "UNAUTHORIZED", "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"));
            }
        };
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken,
            HttpServletResponse response) {
        authService.logout(refreshToken);
        authCookies.clear(response);
        return ResponseEntity.noContent().build();
    }

    /** @AuthenticationPrincipal Jwt: access token đã được Resource Server xác minh; sub = id người dùng. */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.ok(authService.me(Long.valueOf(jwt.getSubject()))));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @CookieValue(name = AuthCookies.REFRESH_TOKEN, required = false) String refreshToken,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(Long.valueOf(jwt.getSubject()), refreshToken, request);
        return ResponseEntity.noContent().build();
    }

    private void setCookies(HttpServletResponse response, SessionTokens tokens) {
        authCookies.setTokens(response, tokens.accessToken(), tokens.accessMaxAge(),
                tokens.refreshToken(), tokens.refreshMaxAge());
    }
}
