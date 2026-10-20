package com.grainwarehouse.auth.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.web.util.WebUtils;

import java.util.Set;

/**
 * Resource Server mặc định đọc token từ header "Authorization: Bearer ...". Ở đây token nằm trong cookie HttpOnly
 * (ADR-0003) nên thay bước "lấy token" — còn xác minh vẫn do JwtDecoder của Spring làm.
 *
 * Bỏ qua các endpoint công khai: cookie GW_AT có Path=/api nên vẫn được gửi tới /api/auth/refresh. Nếu đọc nó,
 * access token VỪA HẾT HẠN sẽ làm request gia hạn bị 401 ngay ở filter — đúng lúc cần gia hạn nhất.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/csrf", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout", "/api/health");

    @Override
    public String resolve(HttpServletRequest request) {
        if (PUBLIC_PATHS.contains(request.getRequestURI())) {
            return null;
        }
        Cookie cookie = WebUtils.getCookie(request, AuthCookies.ACCESS_TOKEN);
        return cookie == null || cookie.getValue().isBlank() ? null : cookie.getValue();
    }
}
