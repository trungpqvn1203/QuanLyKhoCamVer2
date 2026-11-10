package com.grainwarehouse.portal;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.portal.dto.PortalLoginRequest;
import com.grainwarehouse.portal.dto.PortalOrderLineRequest;
import com.grainwarehouse.portal.dto.PortalOrderRequest;
import com.grainwarehouse.portal.service.PortalAccountService;
import com.grainwarehouse.portal.service.PortalAuthService;
import com.grainwarehouse.portal.service.PortalOrderLimits;
import com.grainwarehouse.portal.service.PortalOrderService;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BR-POR-04 với transaction THẬT (không @Transactional ở lớp test): nhiều lần đăng nhập sai cùng lúc vẫn phải
 * được đếm đủ. Không khóa dòng tài khoản thì các request cùng đọc "đã sai 0 lần" rồi cùng ghi 1 (lost update) →
 * kẻ dò mật khẩu gửi song song sẽ không bao giờ bị khóa tạm.
 * BR-POR-12 cùng lý do: không khóa dòng tài khoản thì nhiều đơn gửi cùng lúc cùng đếm "còn chỗ" và vượt giới hạn.
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

    @Autowired
    private PortalOrderService portalOrderService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductUnitRepository unitRepository;

    @Autowired
    private ProductRepository productRepository;

    private Customer customer;
    private Product product;

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
        jdbcTemplate.update("DELETE FROM sale_request_item WHERE document_id IN"
                + " (SELECT id FROM stock_document WHERE customer_id = ?)", customer.getId());
        jdbcTemplate.update("DELETE FROM stock_document WHERE customer_id = ?", customer.getId());
        if (product != null) {
            jdbcTemplate.update("DELETE FROM product WHERE id = ?", product.getId());
            jdbcTemplate.update("DELETE FROM category WHERE id = ?", product.getCategory().getId());
        }
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

    @Test
    @DisplayName("BR-POR-12: 8 đơn app gửi cùng lúc - đúng 5 đơn được tạo, 3 đơn bị từ chối vì đủ 5 đơn chờ")
    void concurrentOrdersShouldRespectPendingLimit() throws Exception {
        Category category = new Category();
        category.setName("Cám test giới hạn đơn " + customer.getPhone());
        categoryRepository.save(category);
        product = new Product();
        product.setName("Cám test giới hạn đơn " + customer.getPhone());
        product.setCategory(category);
        product.setUnit(unitRepository.findByNameIgnoreCase("Bao").orElseThrow());
        product.setNetWeightKg(new BigDecimal("25"));
        productRepository.save(product);
        jdbcTemplate.update("UPDATE customer_account SET must_change_password = FALSE WHERE customer_id = ?",
                customer.getId());
        Long accountId = jdbcTemplate.queryForObject("SELECT id FROM customer_account WHERE customer_id = ?",
                Long.class, customer.getId());
        int requests = 8;
        PortalOrderRequest request = new PortalOrderRequest(null, null,
                List.of(new PortalOrderLineRequest(product.getId(), BigDecimal.ONE)));

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        portalOrderService.create(accountId, request);
                        created.incrementAndGet();
                    } catch (BusinessException expected) {
                        rejected.incrementAndGet();
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

        assertThat(created.get()).isEqualTo(PortalOrderLimits.MAX_PENDING_ORDERS);
        assertThat(rejected.get()).isEqualTo(requests - PortalOrderLimits.MAX_PENDING_ORDERS);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stock_document WHERE customer_id = ?"
                + " AND status = 'PENDING' AND order_channel = 'APP'", Integer.class, customer.getId()))
                .isEqualTo(PortalOrderLimits.MAX_PENDING_ORDERS);
    }
}
