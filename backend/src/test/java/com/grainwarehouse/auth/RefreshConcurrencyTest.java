package com.grainwarehouse.auth;

import com.grainwarehouse.auth.dto.LoginRequest;
import com.grainwarehouse.auth.entity.AppUser;
import com.grainwarehouse.auth.entity.Role;
import com.grainwarehouse.auth.repository.AppUserRepository;
import com.grainwarehouse.auth.service.AuthService;
import com.grainwarehouse.auth.service.AuthService.RefreshResult;
import com.grainwarehouse.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hai tab cùng gia hạn bằng MỘT refresh token, cùng lúc. Không @Transactional: mỗi luồng cần transaction thật,
 * commit thật để khóa dòng (SELECT ... FOR UPDATE) có tác dụng giữa các luồng. Tự dọn dữ liệu ở @AfterEach.
 */
@SpringBootTest
@ActiveProfiles("test")
class RefreshConcurrencyTest {

    private static final int THREADS = 5;

    @Autowired
    private AuthService authService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AppUser user;

    @AfterEach
    void cleanUp() {
        if (user != null) {
            jdbcTemplate.update("UPDATE refresh_token SET replaced_by_id = NULL, replaced_at = NULL WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM refresh_token WHERE user_id = ?", user.getId());
            jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", user.getId());
        }
    }

    @Test
    @DisplayName("5 luồng gia hạn cùng một token: đúng 1 luồng nhận token mới, 4 luồng còn lại REFRESH_RACE, phiên không bị thu hồi")
    void onlyOneConcurrentRefreshWins() throws Exception {
        user = SecurityTestSupport.createUser(userRepository, passwordEncoder, "dong.thoi", true, Role.SALES_STAFF);
        String refreshToken = authService.login(new LoginRequest("dong.thoi", SecurityTestSupport.PASSWORD), "test")
                .tokens().refreshToken();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RefreshResult>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return authService.refresh(refreshToken, "test");
            }));
        }
        start.countDown();
        List<RefreshResult> results = new ArrayList<>();
        for (Future<RefreshResult> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();

        assertThat(results).filteredOn(RefreshResult.Renewed.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(RefreshResult.Race.class::isInstance).hasSize(THREADS - 1);
        // Gốc + đúng 1 token mới; không dòng nào bị thu hồi
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_token WHERE user_id = ?",
                Integer.class, user.getId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_token WHERE user_id = ? AND revoked_at IS NOT NULL",
                Integer.class, user.getId())).isZero();
    }
}
