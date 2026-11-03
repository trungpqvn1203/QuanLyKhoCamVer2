package com.grainwarehouse.portal.web;

import com.grainwarehouse.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cookie token của app khách (ADR-0005 mục 1) — cùng thuộc tính với AuthCookies (HttpOnly, SameSite=Strict, Secure)
 * nhưng KHÁC tên và Path: trình duyệt chỉ gửi GW_CAT tới /api/portal/**, GW_CRT tới /api/portal/auth/**.
 * Cookie nhân viên GW_AT (Path=/api) vẫn tới /api/portal/** nhưng chuỗi portal không đọc nó.
 */
@Component
@RequiredArgsConstructor
public class PortalCookies {

    public static final String ACCESS_TOKEN = "GW_CAT";
    public static final String REFRESH_TOKEN = "GW_CRT";

    static final String ACCESS_PATH = "/api/portal";
    static final String REFRESH_PATH = "/api/portal/auth";

    private final AuthProperties properties;

    public void setTokens(HttpServletResponse response, String accessToken, Duration accessMaxAge,
                          String refreshToken, Duration refreshMaxAge) {
        add(response, cookie(ACCESS_TOKEN, accessToken, ACCESS_PATH, accessMaxAge));
        add(response, cookie(REFRESH_TOKEN, refreshToken, REFRESH_PATH, refreshMaxAge));
    }

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
