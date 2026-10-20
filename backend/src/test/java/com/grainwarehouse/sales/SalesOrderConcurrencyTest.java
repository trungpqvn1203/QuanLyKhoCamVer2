package com.grainwarehouse.sales;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.sales.dto.RecordPaymentRequest;
import com.grainwarehouse.sales.dto.SalesOrderLineRequest;
import com.grainwarehouse.sales.dto.SalesOrderRequest;
import com.grainwarehouse.sales.service.SalesOrderService;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.entity.WarehouseType;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Các ca cần transaction THẬT (không @Transactional ở lớp test — lý do: xem InboundReceiptConcurrencyTest):
 * nhiều luồng cùng bán/thu tiền, và rollback giữa chừng khi một dòng thiếu tồn. Tự dọn dữ liệu ở @AfterEach.
 */
@SpringBootTest
@ActiveProfiles("test")
class SalesOrderConcurrencyTest {

    private static final int THREADS = 5;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private Category category;
    private Warehouse warehouse;
    private Customer customer;
    private Product pigFeed;
    private Product chickenFeed;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Cám bán đồng thời " + tag);
        categoryRepository.save(category);
        warehouse = new Warehouse();
        warehouse.setName("Kho bán đồng thời " + tag);
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Địa chỉ test");
        warehouseRepository.save(warehouse);
        customer = new Customer();
        customer.setName("Khách đồng thời " + tag);
        customer.setType(CustomerType.FARM);
        // SĐT ngẫu nhiên: lần chạy trước dọn dở dang thì vẫn không trùng UNIQUE
        customer.setPhone("09" + ThreadLocalRandom.current().nextLong(10_000_000L, 100_000_000L));
        customerRepository.save(customer);
        // Heo tạo trước → id nhỏ hơn → dòng heo được trừ trước (thứ tự product_id tăng dần)
        pigFeed = saveProduct("Cám heo " + tag);
        chickenFeed = saveProduct("Cám gà " + tag);
    }

    @AfterEach
    void cleanUp() {
        // stock_transaction và sale_payment bất biến: chỉ DB test được tắt trigger tạm thời, trong một transaction
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("ALTER TABLE sale_payment DISABLE TRIGGER trg_sale_payment_immutable");
            jdbcTemplate.update("DELETE FROM sale_payment WHERE document_id IN"
                    + " (SELECT id FROM stock_document WHERE warehouse_id = ?)", warehouse.getId());
            jdbcTemplate.execute("ALTER TABLE sale_payment ENABLE TRIGGER trg_sale_payment_immutable");
            jdbcTemplate.execute("ALTER TABLE stock_transaction DISABLE TRIGGER trg_stock_transaction_immutable");
            jdbcTemplate.update("DELETE FROM stock_transaction WHERE warehouse_id = ?", warehouse.getId());
            jdbcTemplate.execute("ALTER TABLE stock_transaction ENABLE TRIGGER trg_stock_transaction_immutable");
            jdbcTemplate.update("DELETE FROM inventory WHERE warehouse_id = ?", warehouse.getId());
            jdbcTemplate.update("DELETE FROM stock_document_item WHERE document_id IN"
                    + " (SELECT id FROM stock_document WHERE warehouse_id = ?)", warehouse.getId());
            jdbcTemplate.update("DELETE FROM stock_document WHERE warehouse_id = ?", warehouse.getId());
            jdbcTemplate.update("DELETE FROM product WHERE category_id = ?", category.getId());
            jdbcTemplate.update("DELETE FROM category WHERE id = ?", category.getId());
            jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customer.getId());
            jdbcTemplate.update("DELETE FROM warehouse WHERE id = ?", warehouse.getId());
        });
    }

    @Test
    @DisplayName("BR-SAL-04: " + THREADS + " luồng cùng hoàn tất một phiếu - đúng 1 thành công, tồn chỉ trừ một lần")
    void concurrentCompleteShouldApplyOnce() throws Exception {
        setStock(pigFeed, 100);
        long id = createDraft(line(pigFeed, 10));

        List<Throwable> results = runConcurrently(() -> salesOrderService.complete(id));

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).allSatisfy(error -> assertThat(error).isInstanceOf(BusinessException.class));
        assertThat(stock(pigFeed)).isEqualTo(90);
        assertThat(transactionCount(id)).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-INV-01: " + THREADS + " phiếu cùng bán 30 bao khi tồn 100 - đúng 3 thành công, tồn không âm")
    void concurrentSalesShouldNeverOversell() throws Exception {
        setStock(pigFeed, 100);
        List<Callable<Object>> actions = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            long id = createDraft(line(pigFeed, 30));
            actions.add(() -> salesOrderService.complete(id));
        }

        List<Throwable> results = runConcurrently(actions);

        assertThat(successes(results)).isEqualTo(3);
        assertThat(failures(results)).hasSize(THREADS - 3)
                .allSatisfy(error -> assertThat(error).hasMessageContaining("Không đủ tồn"));
        assertThat(stock(pigFeed)).isEqualTo(10);
    }

    @Test
    @DisplayName("BR-SAL-02: phiếu 2 dòng, dòng 2 thiếu tồn - rollback toàn bộ, dòng 1 không bị trừ, phiếu vẫn Nháp")
    void completeShouldRollBackAllLinesWhenOneFails() {
        setStock(pigFeed, 100);
        setStock(chickenFeed, 5);
        long id = createDraft(line(pigFeed, 10), line(chickenFeed, 20));

        assertThatThrownBy(() -> salesOrderService.complete(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(chickenFeed.getCode());

        assertThat(stock(pigFeed)).isEqualTo(100);
        assertThat(stock(chickenFeed)).isEqualTo(5);
        assertThat(transactionCount(id)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM stock_document WHERE id = ?", String.class, id))
                .isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("BR-SAL-11: " + THREADS + " luồng cùng thu 400.000 cho phiếu nợ 1.000.000 - đúng 2 thành công, không thu vượt")
    void concurrentPaymentsShouldNeverOverpay() throws Exception {
        setStock(pigFeed, 100);
        long id = createDraft(new SalesOrderLineRequest(pigFeed.getId(), 10, new BigDecimal("100000")));
        salesOrderService.complete(id);
        RecordPaymentRequest payment = new RecordPaymentRequest(null, new BigDecimal("400000"), null, null);

        List<Throwable> results = runConcurrently(() -> salesOrderService.recordPayment(id, payment));

        assertThat(successes(results)).isEqualTo(2);
        assertThat(failures(results)).allSatisfy(error -> assertThat(error)
                .isInstanceOf(BusinessException.class).hasMessageContaining("vượt số còn nợ"));
        assertThat(jdbcTemplate.queryForObject("SELECT paid_amount FROM stock_document WHERE id = ?",
                BigDecimal.class, id)).isEqualByComparingTo("800000");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sale_payment WHERE document_id = ?",
                Integer.class, id)).isEqualTo(2);
    }

    // ===== Helpers =====

    private List<Throwable> runConcurrently(Callable<Object> action) throws Exception {
        List<Callable<Object>> actions = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            actions.add(action);
        }
        return runConcurrently(actions);
    }

    /** Các tác vụ xuất phát cùng lúc (cổng CountDownLatch). Trả về lỗi của từng tác vụ, null = thành công. */
    private List<Throwable> runConcurrently(List<Callable<Object>> actions) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Callable<Object> action : actions) {
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

    private static long successes(List<Throwable> results) {
        return results.stream().filter(Objects::isNull).count();
    }

    private static List<Throwable> failures(List<Throwable> results) {
        return results.stream().filter(Objects::nonNull).toList();
    }

    private long createDraft(SalesOrderLineRequest... lines) {
        return salesOrderService.create(new SalesOrderRequest(
                customer.getId(), warehouse.getId(), null, null, Arrays.asList(lines))).id();
    }

    private static SalesOrderLineRequest line(Product product, int quantity) {
        return new SalesOrderLineRequest(product.getId(), quantity, new BigDecimal("1000"));
    }

    private void setStock(Product product, int quantity) {
        jdbcTemplate.update("INSERT INTO inventory (product_id, warehouse_id, quantity) VALUES (?, ?, ?)",
                product.getId(), warehouse.getId(), quantity);
    }

    private Integer stock(Product product) {
        return jdbcTemplate.query("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, product.getId(), warehouse.getId());
    }

    private int transactionCount(long documentId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM stock_transaction t"
                + " JOIN stock_document_item i ON i.id = t.document_item_id WHERE i.document_id = ?",
                Integer.class, documentId);
    }

    private Product saveProduct(String name) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setUnit(ProductUnit.BAG);
        product.setNetWeightKg(new BigDecimal("25"));
        return productRepository.save(product);
    }
}
