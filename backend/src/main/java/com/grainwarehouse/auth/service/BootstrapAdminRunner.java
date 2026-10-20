package com.grainwarehouse.auth.service;

import com.grainwarehouse.auth.config.AuthProperties;
import com.grainwarehouse.auth.dto.CreateUserRequest;
import com.grainwarehouse.auth.entity.Role;
import com.grainwarehouse.auth.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Hệ thống mới tinh chưa có tài khoản nào thì không ai đăng nhập để tạo tài khoản được ("con gà – quả trứng").
 * Khi khởi động, nếu bảng app_user trống và có BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD trong .env
 * → tạo một Quản lý hệ thống. Đã có tài khoản thì không làm gì (đổi biến môi trường sau đó không ảnh hưởng).
 * Mật khẩu không nằm trong code, migration hay seed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminRunner implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final UserService userService;
    private final AuthProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        String username = properties.bootstrapAdminUsername();
        String password = properties.bootstrapAdminPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Chưa có tài khoản nào và chưa đặt BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD"
                    + " — không ai đăng nhập được. Xem .env.example");
            return;
        }
        userService.create(new CreateUserRequest(username, "Quản lý hệ thống", password, Set.of(Role.SYSTEM_ADMIN)));
        log.info("Đã tạo tài khoản Quản lý hệ thống đầu tiên: {}", username.trim().toLowerCase());
    }
}
