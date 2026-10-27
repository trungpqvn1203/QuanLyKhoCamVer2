package com.grainwarehouse.stockcount;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.opening.dto.OpeningStockLineRequest;
import com.grainwarehouse.opening.dto.OpeningStockRequest;
import com.grainwarehouse.opening.service.OpeningStockService;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.stockcount.dto.StockCountCreateRequest;
import com.grainwarehouse.stockcount.dto.StockCountLineRequest;
import com.grainwarehouse.stockcount.dto.StockCountUpdateRequest;
import com.grainwarehouse.stockcount.service.StockCountService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Commit thật, nhiều luồng — KHÔNG @Transactional (xem InboundReceiptConcurrencyTest), tự dọn ở @AfterEach. */
@SpringBootTest
@ActiveProfiles("test")
class StockCountConcurrencyTest {

    private static final int THREADS = 5;
    private static final String USER = "admin";

    @Autowired
    private StockCountService stockCountService;

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
    private Product corn;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Cám kiểm kê " + tag);
        categoryRepository.save(category);
        warehouse = new Warehouse();
        warehouse.setName("Kho kiểm kê " + tag);
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Địa chỉ test");
        warehouseRepository.save(warehouse);
        corn = new Product();
        corn.setName("Cám " + tag);
        corn.setCategory(category);
        corn.setUnit(ProductUnit.BAG);
        corn.setNetWeightKg(new BigDecimal("25"));
        productRepository.save(corn);
        long opening = openingStockService.create(new OpeningStockRequest(warehouse.getId(), null, null,
                List.of(new OpeningStockLineRequest(corn.getId(), 120, null))), USER).id();
        openingStockService.complete(opening, USER);
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
    @DisplayName("BR-CNT-07: " + THREADS + " người cùng lập phiếu kiểm cho một kho - đúng 1 phiếu đang kiểm")
    void concurrentCreatesShouldKeepOneOpenCount() throws Exception {
        List<Throwable> results = runConcurrently(() ->
                stockCountService.create(new StockCountCreateRequest(warehouse.getId(), null, null), USER));

        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stock_document WHERE warehouse_id = ?"
                + " AND type = 'STOCK_COUNT' AND status = ?", Integer.class, warehouse.getId(),
                StockDocumentStatus.DRAFT.name())).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-IMP-14: " + THREADS + " luồng cùng xác nhận một phiếu - chênh lệch chỉ ghi một lần")
    void concurrentCompletesShouldApplyOnce() throws Exception {
        long id = stockCountService.create(new StockCountCreateRequest(warehouse.getId(), null, null), USER).id();
        Long reason = jdbcTemplate.queryForObject("SELECT min(id) FROM stock_count_reason WHERE active", Long.class);
        stockCountService.update(id, new StockCountUpdateRequest(null, null,
                List.of(new StockCountLineRequest(corn.getId(), 115, reason, null))));

        List<Throwable> results = runConcurrently(() -> stockCountService.complete(id, USER));

        assertThat(results.stream().filter(Objects::isNull).count()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                Integer.class, corn.getId(), warehouse.getId())).isEqualTo(115);
    }

    @Test
    @DisplayName("BR-CNT-06: dòng thiếu mà tồn hiện tại không đủ trừ - từ chối, rollback cả dòng thừa đã ghi, phiếu vẫn đang kiểm")
    void shortageBeyondStockShouldRollBackEverything() {
        // Tạo sau corn → id lớn hơn → xử lý sau (thứ tự product_id tăng dần): dòng thừa của corn được ghi TRƯỚC
        Product other = new Product();
        other.setName("Cám khác " + tag);
        other.setCategory(category);
        other.setUnit(ProductUnit.BAG);
        other.setNetWeightKg(new BigDecimal("25"));
        productRepository.save(other);
        long opening = openingStockService.create(new OpeningStockRequest(warehouse.getId(), null, null,
                List.of(new OpeningStockLineRequest(other.getId(), 50, null))), USER).id();
        openingStockService.complete(opening, USER);

        long id = stockCountService.create(new StockCountCreateRequest(warehouse.getId(), null, null), USER).id();
        Long reason = jdbcTemplate.queryForObject("SELECT min(id) FROM stock_count_reason WHERE active", Long.class);
        stockCountService.update(id, new StockCountUpdateRequest(null, null, List.of(
                new StockCountLineRequest(corn.getId(), 130, reason, null),     // sổ sách 120 → thừa 10
                new StockCountLineRequest(other.getId(), 40, reason, null))));  // sổ sách 50 → thiếu 10
        // Giả lập sau lúc chụp đã bán 45 bao "other" (commit ngay): tồn còn 5, không đủ trừ 10
        jdbcTemplate.update("UPDATE inventory SET quantity = 5 WHERE product_id = ? AND warehouse_id = ?",
                other.getId(), warehouse.getId());

        assertThatThrownBy(() -> stockCountService.complete(id, USER))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không đủ tồn");

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM stock_document WHERE id = ?", String.class, id))
                .isEqualTo("DRAFT");
        assertThat(quantity(corn)).isEqualTo(120);   // +10 của corn đã bị rollback
        assertThat(quantity(other)).isEqualTo(5);
    }

    private Integer quantity(Product product) {
        return jdbcTemplate.queryForObject("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                Integer.class, product.getId(), warehouse.getId());
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
