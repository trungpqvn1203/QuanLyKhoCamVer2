package com.grainwarehouse.portal;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.portal.dto.PortalLoginRequest;
import com.grainwarehouse.portal.service.PortalAccountService;
import com.grainwarehouse.portal.service.PortalAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BR-POR-04 với transaction THẬT (không @Transactional ở lớp test): nhiều lần đăng nhập sai cùng lúc vẫn phải
 * được đếm đủ. Không khóa dòng tài khoản thì các request cùng đọc "đã sai 0 lần" rồi cùng ghi 1 (lost update) →
 * kẻ dò mật khẩu gửi song song sẽ không bao giờ bị khóa tạm.
 */
@SpringBootTest
@ActiveProfiles("test")
class PortalConcurrencyTest {

    private static final int THREADS = 5;
    private static final String PASSWORD = "matkhau-cua-hang-1";

    @Autowired
    private PortalAuthService portalAuthService;

    @Autowired
    private PortalAccountService portalAccountService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setName("Khách dò mật khẩu");
        customer.setType(CustomerType.RETAIL);
        // SĐT ngẫu nhiên: lần chạy trước dọn dở dang thì vẫn không trùng UNIQUE
        customer.setPhone("09" + ThreadLocalRandom.current().nextLong(10_000_000L, 100_000_000L));
        customerRepository.save(customer);
        portalAccountService.create(customer.getId(), PASSWORD, "test");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM customer_refresh_token WHERE customer_account_id IN"
                + " (SELECT id FROM customer_account WHERE customer_id = ?)", customer.getId());
        jdbcTemplate.update("DELETE FROM customer_account WHERE customer_id = ?", customer.getId());
        jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customer.getId());
    }

    @Test
    @DisplayName("BR-POR-04: " + THREADS + " lần sai mật khẩu gửi cùng lúc - đủ 5 lần được đếm, tài khoản bị khóa tạm")
    void concurrentFailedLoginsShouldAllCount() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                String wrong = "sai-mat-khau-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        portalAuthService.login(new PortalLoginRequest(customer.getPhone(), wrong), "test");
                    } catch (RuntimeException expected) {
                        // sai mật khẩu → BadCredentialsException, đúng như mong đợi
                    }
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT locked_until IS NOT NULL FROM customer_account WHERE customer_id = ?", Boolean.class,
                customer.getId())).isTrue();
        assertThatThrownBy(() -> portalAuthService.login(new PortalLoginRequest(customer.getPhone(), PASSWORD), "test"))
                .isInstanceOf(LockedException.class);
    }
}
