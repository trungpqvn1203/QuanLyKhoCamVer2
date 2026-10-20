package com.grainwarehouse.auth.web;

import com.grainwarehouse.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Đặt / xóa cookie token (ADR-0003 mục 1). Cả hai đều HttpOnly → JavaScript, kể cả mã độc chèn qua XSS, không đọc được.
 * SameSite=Strict → trình duyệt không gửi cookie khi request xuất phát từ trang web khác (lớp chống CSRF thứ nhất).
 */
@Component
@RequiredArgsConstructor
public class AuthCookies {

    public static final String ACCESS_TOKEN = "GW_AT";
    public static final String REFRESH_TOKEN = "GW_RT";

    /** Access token gửi kèm mọi API */
    static final String ACCESS_PATH = "/api";
    /** Refresh token CHỈ gửi tới gia hạn/đăng xuất — các API khác không bao giờ nhận được nó */
    static final String REFRESH_PATH = "/api/auth";

    private final AuthProperties properties;

    public void setTokens(HttpServletResponse response, String accessToken, Duration accessMaxAge,
                          String refreshToken, Duration refreshMaxAge) {
        add(response, cookie(ACCESS_TOKEN, accessToken, ACCESS_PATH, accessMaxAge));
        add(response, cookie(REFRESH_TOKEN, refreshToken, REFRESH_PATH, refreshMaxAge));
    }

    /** Max-Age=0 + đúng Path lúc đặt → trình duyệt xóa cookie (khác Path là cookie khác, không xóa được). */
    public void clear(HttpServletResponse response) {
        add(response, cookie(ACCESS_TOKEN, "", ACCESS_PATH, Duration.ZERO));
        add(response, cookie(REFRESH_TOKEN, "", REFRESH_PATH, Duration.ZERO));
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
