package com.grainwarehouse.opening;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.opening.dto.OpeningStockLineRequest;
import com.grainwarehouse.opening.dto.OpeningStockRequest;
import com.grainwarehouse.opening.service.OpeningStockService;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Các ca cần commit thật: nhiều luồng xác nhận cùng lúc. KHÔNG có @Transactional (xem InboundReceiptConcurrencyTest),
 * tự dọn dữ liệu ở @AfterEach.
 */
@SpringBootTest
@ActiveProfiles("test")
class OpeningStockConcurrencyTest {

    private static final int THREADS = 5;
    private static final String USER = "admin";

    @Autowired
    private OpeningStockService openingStockService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private Category category;
    private Warehouse warehouse;
    private Product cornFeed;
    private Product pigFeed;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Cám đầu kỳ " + tag);
        categoryRepository.save(category);
        warehouse = new Warehouse();
        warehouse.setName("Kho đầu kỳ " + tag);
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Địa chỉ test");
        warehouseRepository.save(warehouse);
        cornFeed = saveProduct("Cám A " + tag);
        pigFeed = saveProduct("Cám B " + tag);
    }

    @AfterEach
    void cleanUp() {
        // Tắt trigger bất biến tạm thời trong cùng transaction — chỉ dùng cho DB test (xem InboundReceiptConcurrencyTest)
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("ALTER TABLE stock_transaction DISABLE TRIGGER trg_stock_transaction_immutable");
            jdbcTemplate.update("DELETE FROM stock_transaction WHERE warehouse_id = ?", warehouse.getId());
            jdbcTemplate.execute("ALTER TABLE stock_transaction ENABLE TRIGGER trg_stock_transaction_immutable");
            jdbcTemplate.update("DELETE FROM inventory WHERE warehouse_id = ?", warehouse.getId());
            jdbcTemplate.update("DELETE FROM stock_document_item WHERE document_id IN"
                    + " (SELECT id FROM stock_document WHERE warehouse_id = ?)", warehouse.getId());
            jdbcTemplate.update("DELETE FROM stock_document WHERE warehouse_id = ?", warehouse.getId());
            jdbcTemplate.update("DELETE FROM product WHERE category_id = ?", category.getId());
            jdbcTemplate.update("DELETE FROM category WHERE id = ?", category.getId());
            jdbcTemplate.update("DELETE FROM warehouse WHERE id = ?", warehouse.getId());
        });
    }

    @Test
    @DisplayName("BR-IMP-14: " + THREADS + " luồng cùng xác nhận một phiếu (bấm hai lần) - đúng 1 thành công, tồn cộng một lần")
    void concurrentConfirmOfSameDocumentShouldApplyOnce() throws Exception {
        long id = createDraft(line(cornFeed, 500), line(pigFeed, 300));

        List<Throwable> results = runConcurrently(nTimes(() -> openingStockService.complete(id, USER)));

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).hasSize(THREADS - 1)
                .allSatisfy(error -> assertThat(error).isInstanceOf(BusinessException.class));
        assertThat(inventory(cornFeed)).isEqualTo(500);
        assertThat(inventory(pigFeed)).isEqualTo(300);
        assertThat(transactionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("BR-OPN-05: " + THREADS + " phiếu KHÁC NHAU cùng khởi tạo một SP tại một kho, xác nhận cùng lúc"
            + " - đúng 1 thành công, tồn không bị cộng dồn")
    void concurrentOpeningsOfSamePairShouldApplyOnce() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= THREADS; i++) {
            ids.add(createDraft(line(cornFeed, i * 100)));
        }

        List<Throwable> results = runConcurrently(ids.stream()
                .<Callable<Object>>map(id -> () -> openingStockService.complete(id, USER))
                .toList());

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).hasSize(THREADS - 1)
                .allSatisfy(error -> assertThat(error).isInstanceOf(BusinessException.class));
        // Tồn bằng đúng SL của phiếu thắng (100, 200, ... hoặc 500), không phải tổng các phiếu
        Integer stock = inventory(cornFeed);
        assertThat(stock).isIn(100, 200, 300, 400, 500);
        assertThat(transactionCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stock_document WHERE warehouse_id = ?"
                + " AND status = 'COMPLETED'", Integer.class, warehouse.getId())).isEqualTo(1);
    }

    // ===== Helpers =====

    private List<Callable<Object>> nTimes(Callable<Object> action) {
        List<Callable<Object>> actions = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            actions.add(action);
        }
        return actions;
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

    private long createDraft(OpeningStockLineRequest... lines) {
        return openingStockService.create(
                new OpeningStockRequest(warehouse.getId(), null, null, Arrays.asList(lines)), USER).id();
    }

    private static OpeningStockLineRequest line(Product product, int quantity) {
        return new OpeningStockLineRequest(product.getId(), quantity, null);
    }

    private Integer inventory(Product product) {
        return jdbcTemplate.query("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, product.getId(), warehouse.getId());
    }

    private int transactionCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM stock_transaction WHERE warehouse_id = ?",
                Integer.class, warehouse.getId());
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
