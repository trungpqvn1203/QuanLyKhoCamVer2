package com.grainwarehouse.common.controller;

import com.grainwarehouse.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkHealth() {
        Map<String, Object> data = new HashMap<>();
        try {
            // Kiểm tra kết nối DB và đọc thông tin từ bảng app_info (Flyway migration V1)
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_info", Integer.class);
            data.put("status", "UP");
            data.put("database", "CONNECTED");
            data.put("appInfoCount", count != null ? count : 0);
            return ResponseEntity.ok(ApiResponse.ok(data, "System is healthy"));
        } catch (Exception e) {
            // Chi tiết lỗi (host, user DB...) chỉ ghi log, không trả ra ngoài
            log.error("Health check: không kết nối được database", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("DATABASE_UNAVAILABLE", "Không kết nối được cơ sở dữ liệu"));
        }
    }
}
