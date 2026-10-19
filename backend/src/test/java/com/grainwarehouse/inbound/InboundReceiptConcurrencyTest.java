package com.grainwarehouse.inbound;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.inbound.dto.InboundReceiptLineRequest;
import com.grainwarehouse.inbound.dto.InboundReceiptRequest;
import com.grainwarehouse.inbound.service.InboundReceiptService;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.SupplierRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Các ca cần transaction THẬT, commit thật: nhiều luồng đồng thời, và rollback giữa chừng.
 * Lớp này KHÔNG có @Transactional — nếu có, mọi thứ chạy chung một transaction của test, các luồng
 * không thấy dữ liệu của nhau và không thể kiểm chứng rollback của service.
 * Hệ quả: phải tự dọn dữ liệu ở @AfterEach (các test khác giả định bảng trống).
 */
@SpringBootTest
@ActiveProfiles("test")
class InboundReceiptConcurrencyTest {

    private static final int THREADS = 5;

    @Autowired
    private InboundReceiptService inboundReceiptService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    // Hậu tố ngẫu nhiên: nếu lần chạy trước dọn dở dang, tên mới vẫn không trùng UNIQUE
    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private Category category;
    private Warehouse warehouse;
    private Supplier supplier;
    private Product pigFeed;
    private Product chickenFeed;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Cám đồng thời " + tag);
        categoryRepository.save(category);
        warehouse = new Warehouse();
        warehouse.setName("Kho đồng thời " + tag);
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Địa chỉ test");
        warehouseRepository.save(warehouse);
        supplier = new Supplier();
        supplier.setName("NCC đồng thời " + tag);
        supplier.setContactName("Nguyễn Văn A");
        supplierRepository.save(supplier);
        // Tạo heo trước gà → id heo nhỏ hơn → dòng heo được xử lý trước (thứ tự product_id tăng dần)
        pigFeed = saveLinkedProduct("Cám heo " + tag);
        chickenFeed = saveLinkedProduct("Cám gà " + tag);
    }

    @AfterEach
    void cleanUp() {
        // stock_transaction bất biến (trigger chặn DELETE). Riêng việc dọn DB test được tắt trigger
        // tạm thời, trong cùng một transaction: lỗi giữa chừng → rollback → trigger tự bật lại.
        // Không bao giờ làm việc này với dữ liệu thật.
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.execute("ALTER TABLE stock_transaction DISABLE TRIGGER trg_stock_transaction_immutable");
            jdbcTemplate.update("DELETE FROM stock_transaction WHERE warehouse_id = ?", warehouse.getId());
            jdbcTemplate.execute("ALTER TABLE stock_transaction ENABLE TRIGGER trg_stock_transaction_immutable");
            jdbcTemplate.update("DELETE FROM inventory WHERE warehouse_id = ?", warehouse.getId());
            jdbcTemplate.update("DELETE FROM stock_document_item WHERE document_id IN"
                    + " (SELECT id FROM stock_document WHERE warehouse_id = ?)", warehouse.getId());
            jdbcTemplate.update("DELETE FROM stock_document WHERE warehouse_id = ?", warehouse.getId());
            jdbcTemplate.update("DELETE FROM product_supplier WHERE supplier_id = ?", supplier.getId());
            jdbcTemplate.update("DELETE FROM product WHERE category_id = ?", category.getId());
            jdbcTemplate.update("DELETE FROM category WHERE id = ?", category.getId());
            jdbcTemplate.update("DELETE FROM supplier WHERE id = ?", supplier.getId());
            jdbcTemplate.update("DELETE FROM warehouse WHERE id = ?", warehouse.getId());
        });
    }

    @Test
    @DisplayName("BR-IMP-14: " + THREADS + " luồng cùng hoàn tất một phiếu - đúng 1 thành công, tồn chỉ cộng một lần")
    void concurrentCompleteShouldApplyOnce() throws Exception {
        long id = createDraft(line(pigFeed, 10), line(chickenFeed, 4));

        List<Throwable> results = runConcurrently(() -> inboundReceiptService.complete(id));

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).hasSize(THREADS - 1)
                .allSatisfy(error -> assertThat(error).isInstanceOf(BusinessException.class)
                        .hasMessageContaining("Hoàn tất"));
        assertThat(inventory(pigFeed)).isEqualTo(10);
        assertThat(inventory(chickenFeed)).isEqualTo(4);
        assertThat(transactionCount(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("BR-IMP-12, BR-IMP-14: " + THREADS + " luồng cùng đảo một phiếu - đúng 1 thành công, tồn chỉ trừ một lần")
    void concurrentReverseShouldApplyOnce() throws Exception {
        long id = createDraft(line(pigFeed, 10));
        inboundReceiptService.complete(id);

        List<Throwable> results = runConcurrently(() -> inboundReceiptService.reverse(id, "Nhập nhầm"));

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).hasSize(THREADS - 1)
                .allSatisfy(error -> assertThat(error).isInstanceOf(BusinessException.class)
                        .hasMessageContaining("chỉ được đảo một lần"));
        assertThat(inventory(pigFeed)).isZero();
        assertThat(transactionCount(id)).isEqualTo(2); // 1 IN + 1 OUT
    }

    @Test
    @DisplayName("Nhiều phiếu cùng nhập SP mới vào cùng kho một lúc - không lỗi trùng UNIQUE, tồn = tổng")
    void concurrentCompletesOfDifferentReceiptsShouldSumUp() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= THREADS; i++) {
            ids.add(createDraft(line(pigFeed, i)));
        }
        List<Callable<Object>> actions = ids.stream()
                .<Callable<Object>>map(id -> () -> inboundReceiptService.complete(id))
                .toList();

        List<Throwable> results = runConcurrently(actions);

        assertThat(failures(results)).isEmpty();
        int expected = THREADS * (THREADS + 1) / 2; // 1 + 2 + ... + THREADS
        assertThat(inventory(pigFeed)).isEqualTo(expected);
        // Chuỗi before → after nối liền nhau: không có lần cộng nào bị mất (lost update)
        List<int[]> snapshots = jdbcTemplate.query("SELECT quantity_before, quantity_after FROM stock_transaction"
                        + " WHERE product_id = ? ORDER BY quantity_before",
                (rs, row) -> new int[] {rs.getInt(1), rs.getInt(2)}, pigFeed.getId());
        assertThat(snapshots).hasSize(THREADS);
        assertThat(snapshots.get(0)[0]).isZero();
        for (int i = 1; i < snapshots.size(); i++) {
            assertThat(snapshots.get(i)[0]).isEqualTo(snapshots.get(i - 1)[1]);
        }
        assertThat(snapshots.get(THREADS - 1)[1]).isEqualTo(expected);
    }

    @Test
    @DisplayName("BR-IMP-06: đảo phiếu 2 dòng, dòng thứ 2 không đủ tồn - rollback toàn bộ, dòng 1 không bị trừ")
    void reverseShouldRollBackAllLinesWhenOneFails() {
        long id = createDraft(line(pigFeed, 10), line(chickenFeed, 20));
        inboundReceiptService.complete(id);
        // Giả lập đã xuất 15 bao gà (module xuất kho chưa có) — commit ngay
        jdbcTemplate.update("UPDATE inventory SET quantity = 5 WHERE product_id = ? AND warehouse_id = ?",
                chickenFeed.getId(), warehouse.getId());

        // Heo (id nhỏ hơn) được trừ trước và thành công, rồi gà thất bại
        assertThatThrownBy(() -> inboundReceiptService.reverse(id, "Nhập nhầm"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(chickenFeed.getCode());

        assertThat(inventory(pigFeed)).isEqualTo(10);   // phần trừ heo đã bị rollback
        assertThat(inventory(chickenFeed)).isEqualTo(5);
        assertThat(transactionCount(id)).isEqualTo(2);  // chỉ còn 2 giao dịch IN, không có OUT nào
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM stock_document WHERE id = ?", String.class, id))
                .isEqualTo("COMPLETED");
    }

    // ===== Helpers =====

    private List<Throwable> runConcurrently(Callable<Object> action) throws Exception {
        List<Callable<Object>> actions = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            actions.add(action);
        }
        return runConcurrently(actions);
    }

    /**
     * Chạy các tác vụ trên nhiều luồng, xuất phát cùng lúc (cổng CountDownLatch) để tăng khả năng chồng lấn.
     * Trả về lỗi của từng tác vụ, null = thành công.
     */
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

    private long createDraft(InboundReceiptLineRequest... lines) {
        return inboundReceiptService.create(new InboundReceiptRequest(
                supplier.getId(), warehouse.getId(), null, null, Arrays.asList(lines))).id();
    }

    private static InboundReceiptLineRequest line(Product product, int quantity) {
        return new InboundReceiptLineRequest(product.getId(), quantity, null, new BigDecimal("1000"));
    }

    private Integer inventory(Product product) {
        return jdbcTemplate.query("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, product.getId(), warehouse.getId());
    }

    private int transactionCount(long documentId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM stock_transaction t"
                + " JOIN stock_document_item i ON i.id = t.document_item_id WHERE i.document_id = ?",
                Integer.class, documentId);
    }

    private Product saveLinkedProduct(String name) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setUnit(ProductUnit.BAG);
        product.setNetWeightKg(new BigDecimal("25"));
        productRepository.save(product);
        // Ghi thẳng SQL: ngoài transaction, supplier/product đã detached — save() liên kết sẽ thành merge()
        jdbcTemplate.update("INSERT INTO product_supplier (supplier_id, product_id) VALUES (?, ?)",
                supplier.getId(), product.getId());
        return product;
    }
}
