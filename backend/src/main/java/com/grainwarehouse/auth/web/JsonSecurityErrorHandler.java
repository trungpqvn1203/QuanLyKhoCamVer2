package com.grainwarehouse.auth.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grainwarehouse.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Lỗi xảy ra trong chuỗi filter bảo mật — TRƯỚC khi tới controller — nên GlobalExceptionHandler không bắt được.
 * Lớp này trả cùng envelope lỗi theo contract-spec thay cho trang lỗi mặc định của Spring.
 */
@Component
@RequiredArgsConstructor
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 401: chưa đăng nhập, hoặc access token hết hạn / sai chữ ký → frontend thử gia hạn một lần. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Chưa đăng nhập hoặc phiên đăng nhập đã hết hạn");
    }

    /** 403: đã biết là ai nhưng không đủ quyền, hoặc request ghi thiếu/sai CSRF token. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        String message = ex instanceof CsrfException
                ? "Thiếu hoặc sai mã chống giả mạo (CSRF). Tải lại trang rồi thử lại"
                : "Bạn không có quyền thực hiện thao tác này";
        write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private void write(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
    }
}
