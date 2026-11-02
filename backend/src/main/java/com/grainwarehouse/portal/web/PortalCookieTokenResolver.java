package com.grainwarehouse.portal.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.web.util.WebUtils;

import java.util.Set;

/**
 * Chuỗi portal chỉ đọc cookie GW_CAT — không bao giờ đọc GW_AT của nhân viên, không đọc header Authorization.
 * Bỏ qua endpoint công khai vì cùng lý do với CookieBearerTokenResolver: access token vừa hết hạn không được
 * làm hỏng request gia hạn.
 */
public class PortalCookieTokenResolver implements BearerTokenResolver {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/portal/auth/login", "/api/portal/auth/refresh", "/api/portal/auth/logout");

    @Override
    public String resolve(HttpServletRequest request) {
        if (PUBLIC_PATHS.contains(request.getRequestURI())) {
            return null;
        }
        Cookie cookie = WebUtils.getCookie(request, PortalCookies.ACCESS_TOKEN);
        return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
    }
}
