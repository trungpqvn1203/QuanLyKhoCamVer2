package com.grainwarehouse.transfer;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.opening.dto.OpeningStockLineRequest;
import com.grainwarehouse.opening.dto.OpeningStockRequest;
import com.grainwarehouse.opening.service.OpeningStockService;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import com.grainwarehouse.sales.dto.SalesOrderLineRequest;
import com.grainwarehouse.sales.dto.SalesOrderRequest;
import com.grainwarehouse.sales.service.SalesOrderService;
import com.grainwarehouse.transfer.dto.TransferLineRequest;
import com.grainwarehouse.transfer.dto.TransferRequest;
import com.grainwarehouse.transfer.service.TransferService;
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
 * Các ca cần commit / rollback THẬT của điều chuyển: rollback giữa chừng (BR-TRF-02, 03) và nhiều luồng cùng lúc
 * (BR-TRF-04). KHÔNG có @Transactional ở lớp test — mỗi lời gọi service là một transaction riêng như khi chạy thật
 * (xem InboundReceiptConcurrencyTest); dữ liệu tự dọn ở @AfterEach.
 *
 * <p>k1 tạo trước nên id nhỏ hơn k2: k1 → k2 = trừ nguồn trước, k2 → k1 = cộng đích trước (với mỗi SP, kho có id nhỏ hơn
 * được đụng trước — thứ tự khóa (SP, kho) của BR-TRF-04).
 */
@SpringBootTest
@ActiveProfiles("test")
class TransferConcurrencyTest {

    private static final int THREADS = 5;
    private static final String USER = "admin";

    @Autowired
    private TransferService transferService;

    @Autowired
    private OpeningStockService openingStockService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductUnitRepository unitRepository;

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
    private Warehouse k1;
    private Warehouse k2;
    private Product cornFeed;
    private Product pigFeed;
    private Long customerId;   // chỉ ca có phiếu bán mới tạo

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Cám điều chuyển " + tag);
        categoryRepository.save(category);
        k1 = saveWarehouse("Kho K1 " + tag);
        k2 = saveWarehouse("Kho K2 " + tag);
        assertThat(k1.getId()).isLessThan(k2.getId());
        cornFeed = saveProduct("Cám A " + tag);
        pigFeed = saveProduct("Cám B " + tag);
        assertThat(cornFeed.getId()).isLessThan(pigFeed.getId());
    }

    @AfterEach
    void cleanUp() {
        // Tắt trigger bất biến tạm thời trong cùng transaction — chỉ dùng cho DB test (xem InboundReceiptConcurrencyTest)
        transactionTemplate.executeWithoutResult(status -> {
            Object[] ids = {k1.getId(), k2.getId()};
            jdbcTemplate.execute("ALTER TABLE stock_transaction DISABLE TRIGGER trg_stock_transaction_immutable");
            jdbcTemplate.update("DELETE FROM stock_transaction WHERE warehouse_id IN (?, ?)", ids);
            jdbcTemplate.execute("ALTER TABLE stock_transaction ENABLE TRIGGER trg_stock_transaction_immutable");
            jdbcTemplate.update("DELETE FROM inventory WHERE warehouse_id IN (?, ?)", ids);
            jdbcTemplate.update("DELETE FROM stock_document_item WHERE document_id IN"
                    + " (SELECT id FROM stock_document WHERE warehouse_id IN (?, ?))", ids);
            jdbcTemplate.update("DELETE FROM stock_document WHERE warehouse_id IN (?, ?)", ids);
            if (customerId != null) {
                jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customerId);
            }
            jdbcTemplate.update("DELETE FROM product WHERE category_id = ?", category.getId());
            jdbcTemplate.update("DELETE FROM category WHERE id = ?", category.getId());
            jdbcTemplate.update("DELETE FROM warehouse WHERE id IN (?, ?)", ids);
        });
    }

    // ===== Rollback thật =====

    @Test
    @DisplayName("BR-TRF-02, 03: (trừ nguồn trước) dòng 2 thiếu tồn - dòng 1 đã trừ được trả lại, đích không được cộng,"
            + " không có giao dịch nào, phiếu vẫn Nháp")
    void shouldRollbackEarlierLinesWhenLaterLineIsShort() {
        seed(k1, line(cornFeed, 100), line(pigFeed, 10));
        int before = transactionCount();
        long id = draft(k1, k2, line(cornFeed, 30), line(pigFeed, 11));

        assertThatThrownBy(() -> transferService.complete(id, USER))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Không đủ tồn");

        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(inventory(cornFeed, k1)).isEqualTo(100);
        assertThat(inventory(pigFeed, k1)).isEqualTo(10);
        assertThat(inventory(cornFeed, k2)).isNull();
        assertThat(transactionCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("BR-TRF-02, 03: (cộng đích trước, vì kho đích id nhỏ hơn) nguồn thiếu tồn - phần đã cộng vào đích"
            + " bị rollback, không có trạng thái chỉ một kho được cập nhật")
    void shouldRollbackTargetIncreaseWhenSourceIsShort() {
        seed(k2, line(cornFeed, 20));
        int before = transactionCount();
        long id = draft(k2, k1, line(cornFeed, 21));

        assertThatThrownBy(() -> transferService.complete(id, USER))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Không đủ tồn");

        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(inventory(cornFeed, k2)).isEqualTo(20);
        assertThat(inventory(cornFeed, k1)).isNull();
        assertThat(transactionCount()).isEqualTo(before);
    }

    @Test
    @DisplayName("BR-TRF-12: đảo khi kho đích không còn đủ hàng - kho nguồn (đảo trước, vì id nhỏ hơn) không được cộng lại,"
            + " phiếu vẫn Hoàn tất")
    void shouldRollbackReverseWhenTargetIsShort() {
        seed(k1, line(cornFeed, 100));
        long id = draft(k1, k2, line(cornFeed, 30));
        transferService.complete(id, USER);
        // Kho đích chuyển trả 5 bao bằng phiếu khác → chỉ còn 25, không đủ 30 để đảo phiếu đầu
        transferService.complete(draft(k2, k1, line(cornFeed, 5)), USER);
        int before = transactionCount();

        assertThatThrownBy(() -> transferService.reverse(id, "Chuyển nhầm"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Không đủ tồn");

        assertThat(statusOf(id)).isEqualTo("COMPLETED");
        assertThat(inventory(cornFeed, k1)).isEqualTo(75);
        assertThat(inventory(cornFeed, k2)).isEqualTo(25);
        assertThat(transactionCount()).isEqualTo(before);
    }

    // ===== Đồng thời =====

    @Test
    @DisplayName("BR-IMP-14: " + THREADS + " luồng cùng hoàn tất một phiếu (bấm nhiều lần) - đúng 1 thành công,"
            + " hàng chỉ chuyển một lần")
    void concurrentCompleteOfSameDocumentShouldApplyOnce() throws Exception {
        seed(k1, line(cornFeed, 100));
        int before = transactionCount();
        long id = draft(k1, k2, line(cornFeed, 30));

        List<Throwable> results = runConcurrently(nTimes(() -> transferService.complete(id, USER)));

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).hasSize(THREADS - 1)
                .allSatisfy(error -> assertThat(error).isInstanceOf(BusinessException.class));
        assertThat(inventory(cornFeed, k1)).isEqualTo(70);
        assertThat(inventory(cornFeed, k2)).isEqualTo(30);
        assertThat(transactionCount()).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("BR-INV-01: " + THREADS + " phiếu × 30 bao cùng rút từ tồn 100 - đúng 3 thành công, nguồn còn 10,"
            + " đích nhận 90, không âm")
    void concurrentTransfersShouldNotOverdrawSource() throws Exception {
        seed(k1, line(cornFeed, 100));
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            ids.add(draft(k1, k2, line(cornFeed, 30)));
        }

        List<Throwable> results = runConcurrently(ids.stream()
                .<Callable<Object>>map(id -> () -> transferService.complete(id, USER))
                .toList());

        assertThat(successes(results)).isEqualTo(3);
        assertThat(failures(results)).hasSize(2)
                .allSatisfy(error -> assertThat(error).isInstanceOf(BusinessException.class));
        assertThat(inventory(cornFeed, k1)).isEqualTo(10);
        assertThat(inventory(cornFeed, k2)).isEqualTo(90);
    }

    @Test
    @DisplayName("BR-TRF-04: 10 phiếu K1→K2 và 10 phiếu K2→K1 (2 SP mỗi phiếu) hoàn tất cùng lúc - không deadlock,"
            + " tất cả thành công, tổng tồn bảo toàn")
    void opposingTransfersShouldNotDeadlock() throws Exception {
        seed(k1, line(cornFeed, 1000), line(pigFeed, 1000));
        seed(k2, line(cornFeed, 1000), line(pigFeed, 1000));
        List<Callable<Object>> actions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            // Dòng hàng cố ý khai ngược thứ tự nhau: service phải tự sắp lại theo product_id
            long forward = draft(k1, k2, line(cornFeed, 3), line(pigFeed, 4));
            long backward = draft(k2, k1, line(pigFeed, 1), line(cornFeed, 2));
            actions.add(() -> transferService.complete(forward, USER));
            actions.add(() -> transferService.complete(backward, USER));
        }

        List<Throwable> results = runConcurrently(actions);

        // Deadlock sẽ hiện ra ở đây dưới dạng CannotAcquireLockException (PostgreSQL hủy một trong hai transaction)
        assertThat(failures(results)).isEmpty();
        // Mỗi cặp: cám A k1 −3 +2, cám B k1 −4 +1
        assertThat(inventory(cornFeed, k1)).isEqualTo(990);
        assertThat(inventory(cornFeed, k2)).isEqualTo(1010);
        assertThat(inventory(pigFeed, k1)).isEqualTo(970);
        assertThat(inventory(pigFeed, k2)).isEqualTo(1030);
    }

    @Test
    @DisplayName("BR-TRF-04: 10 phiếu điều chuyển K1→K2 và 10 lần xuất chuyến giao lấy hàng từ cả hai kho cùng lúc"
            + " - không deadlock: hai nghiệp vụ phải khóa dòng tồn theo CÙNG thứ tự (SP, kho)")
    void transfersAndDeliveryDispatchShouldNotDeadlock() throws Exception {
        seed(k1, line(cornFeed, 1000), line(pigFeed, 1000));
        seed(k2, line(cornFeed, 1000), line(pigFeed, 1000));
        Customer customer = new Customer();
        customer.setName("Khách điều chuyển " + tag);
        customer.setType(CustomerType.DEALER);
        customer.setPhone("07%08d".formatted(Math.floorMod(tag.hashCode(), 100_000_000)));
        customerId = customerRepository.save(customer).getId();

        List<Callable<Object>> actions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long transfer = draft(k1, k2, line(cornFeed, 3), line(pigFeed, 4));
            // Một "chuyến" gồm hai phiếu bán: cám B từ K1 và cám A từ K2. Xuất chuyến khóa (A, K2) rồi (B, K1);
            // điều chuyển viết theo (kho, SP) sẽ khóa (B, K1) rồi mới tới (A, K2) → hai bên giữ chéo nhau
            List<Long> orders = List.of(saleDraft(k1, pigFeed, 2), saleDraft(k2, cornFeed, 1));
            actions.add(() -> transferService.complete(transfer, USER));
            actions.add(() -> transactionTemplate.execute(status -> {
                salesOrderService.completeForDelivery(orders, "CG-TEST");
                return null;
            }));
        }

        List<Throwable> results = runConcurrently(actions);

        assertThat(failures(results)).isEmpty();
        assertThat(inventory(cornFeed, k1)).isEqualTo(970);     // −3 × 10
        assertThat(inventory(cornFeed, k2)).isEqualTo(1020);    // +3 × 10 − 1 × 10
        assertThat(inventory(pigFeed, k1)).isEqualTo(940);      // −4 × 10 − 2 × 10
        assertThat(inventory(pigFeed, k2)).isEqualTo(1040);     // +4 × 10
    }

    // ===== Helpers =====

    private long saleDraft(Warehouse warehouse, Product product, int quantity) {
        return salesOrderService.create(new SalesOrderRequest(customerId, warehouse.getId(), null, null,
                List.of(new SalesOrderLineRequest(product.getId(), quantity, new BigDecimal("1000")))), USER).id();
    }

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
                results.add(future.get(60, TimeUnit.SECONDS));
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

    /** Cho kho có sẵn hàng bằng phiếu tồn đầu kỳ. */
    private void seed(Warehouse warehouse, TransferLineRequest... lines) {
        List<OpeningStockLineRequest> openingLines = Arrays.stream(lines)
                .map(line -> new OpeningStockLineRequest(line.productId(), line.quantity(), null))
                .toList();
        long id = openingStockService.create(
                new OpeningStockRequest(warehouse.getId(), null, null, openingLines), USER).id();
        openingStockService.complete(id, USER);
    }

    private long draft(Warehouse source, Warehouse target, TransferLineRequest... lines) {
        return transferService.create(
                new TransferRequest(source.getId(), target.getId(), null, null, Arrays.asList(lines)), USER).id();
    }

    private static TransferLineRequest line(Product product, int quantity) {
        return new TransferLineRequest(product.getId(), quantity);
    }

    private Integer inventory(Product product, Warehouse warehouse) {
        return jdbcTemplate.query("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?",
                rs -> rs.next() ? rs.getInt(1) : null, product.getId(), warehouse.getId());
    }

    private int transactionCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM stock_transaction WHERE warehouse_id IN (?, ?)",
                Integer.class, k1.getId(), k2.getId());
    }

    private String statusOf(long documentId) {
        return jdbcTemplate.queryForObject("SELECT status FROM stock_document WHERE id = ?", String.class, documentId);
    }

    private Product saveProduct(String name) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setUnit(unitRepository.findByNameIgnoreCase("Bao").orElseThrow());
        product.setNetWeightKg(new BigDecimal("25"));
        return productRepository.save(product);
    }

    private Warehouse saveWarehouse(String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setName(name);
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Địa chỉ test");
        return warehouseRepository.save(warehouse);
    }
}
