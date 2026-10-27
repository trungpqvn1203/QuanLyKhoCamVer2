package com.grainwarehouse.opening;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.opening.dto.OpeningDebtLineRequest;
import com.grainwarehouse.opening.dto.OpeningDebtParty;
import com.grainwarehouse.opening.dto.OpeningDebtRequest;
import com.grainwarehouse.opening.service.OpeningDebtService;
import com.grainwarehouse.sales.dto.RecordPaymentRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Ghi đồng thời, commit thật — KHÔNG @Transactional (xem InboundReceiptConcurrencyTest), tự dọn ở @AfterEach. */
@SpringBootTest
@ActiveProfiles("test")
class OpeningDebtConcurrencyTest {

    private static final int THREADS = 5;

    @Autowired
    private OpeningDebtService openingDebtService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setName("Khách nợ cũ " + System.nanoTime());
        customer.setType(CustomerType.DEALER);
        // SĐT 10 số, gần như không trùng giữa các lần chạy (ck_customer_phone, uk_customer_phone)
        customer.setPhone("08%08d".formatted(System.nanoTime() % 100_000_000));
        customerRepository.save(customer);
    }

    @AfterEach
    void cleanUp() {
        // sale_payment bất biến: chỉ DB test được tắt trigger tạm thời, trong một transaction (lỗi → trigger tự bật lại)
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("ALTER TABLE sale_payment DISABLE TRIGGER trg_sale_payment_immutable");
            jdbcTemplate.update("DELETE FROM sale_payment WHERE document_id IN"
                    + " (SELECT id FROM stock_document WHERE customer_id = ?)", customer.getId());
            jdbcTemplate.execute("ALTER TABLE sale_payment ENABLE TRIGGER trg_sale_payment_immutable");
            jdbcTemplate.update("DELETE FROM stock_document WHERE customer_id = ?", customer.getId());
            jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customer.getId());
        });
    }

    @Test
    @DisplayName("BR-ODB-03: " + THREADS + " người cùng ghi nợ đầu kỳ cho một khách - đúng 1 khoản được ghi")
    void concurrentCreatesForSameCustomerShouldKeepOne() throws Exception {
        List<Throwable> results = runConcurrently(() -> openingDebtService.create(OpeningDebtParty.CUSTOMER,
                new OpeningDebtRequest(null, List.of(new OpeningDebtLineRequest(customer.getId(),
                        new BigDecimal("15000000"), null))), "admin"));

        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stock_document WHERE customer_id = ?"
                + " AND type = 'OPENING_CUSTOMER_DEBT'", Integer.class, customer.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-ODB-07: " + THREADS + " người cùng thu hết nợ cũ - chỉ 1 lần thu được, không thu vượt")
    void concurrentPaymentsShouldNotOverpay() throws Exception {
        long id = openingDebtService.create(OpeningDebtParty.CUSTOMER, new OpeningDebtRequest(null,
                List.of(new OpeningDebtLineRequest(customer.getId(), new BigDecimal("1000000"), null))), "admin")
                .get(0).id();

        List<Throwable> results = runConcurrently(() -> openingDebtService.recordPayment(OpeningDebtParty.CUSTOMER, id,
                new RecordPaymentRequest(null, new BigDecimal("1000000"), null, null)));

        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT paid_amount FROM stock_document WHERE id = ?", Long.class, id))
                .isEqualTo(1000000);
    }

    private List<Throwable> runConcurrently(Callable<Object> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        action.call();
                        return null;
                    } catch (Throwable error) {
                        return error;
                    }
                }));
            }
            ready.await();
            start.countDown();
            List<Throwable> results = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
