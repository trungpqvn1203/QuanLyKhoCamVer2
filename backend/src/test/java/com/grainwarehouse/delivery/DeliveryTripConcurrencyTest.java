package com.grainwarehouse.delivery;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
import com.grainwarehouse.delivery.dto.DeliveryTripRequest;
import com.grainwarehouse.delivery.entity.Vehicle;
import com.grainwarehouse.delivery.repository.VehicleRepository;
import com.grainwarehouse.delivery.service.DeliveryTripService;
import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.employee.repository.EmployeeRepository;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
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
import java.time.LocalDate;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Xuất chuyến cần transaction THẬT (không @Transactional ở lớp test): nhiều luồng cùng xuất / cùng xếp, và rollback
 * khi đã trừ tồn một phần rồi mới thiếu hàng. Tự dọn dữ liệu ở @AfterEach.
 */
@SpringBootTest
@ActiveProfiles("test")
class DeliveryTripConcurrencyTest {

    private static final int THREADS = 5;

    @Autowired
    private DeliveryTripService tripService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductUnitRepository unitRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final String tag = UUID.randomUUID().toString().substring(0, 8);
    private Category category;
    private Warehouse warehouse;
    private Customer customer;
    private Employee driver;
    private Vehicle vehicle;
    // Tạo trước → id nhỏ hơn → được trừ TRƯỚC khi xuất chuyến (thứ tự SP tăng dần)
    private Product premix;
    private Product pigFeed;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Cám chuyến đồng thời " + tag);
        categoryRepository.save(category);
        warehouse = new Warehouse();
        warehouse.setName("Kho chuyến đồng thời " + tag);
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setAddress("Địa chỉ test");
        warehouseRepository.save(warehouse);
        customer = new Customer();
        customer.setName("Khách chuyến đồng thời " + tag);
        customer.setType(CustomerType.FARM);
        customer.setPhone("09" + ThreadLocalRandom.current().nextLong(10_000_000L, 100_000_000L));
        customerRepository.save(customer);
        driver = new Employee();
        driver.setName("Lái xe " + tag);
        driver.setDailyWage(new BigDecimal("300000"));
        employeeRepository.save(driver);
        vehicle = new Vehicle();
        vehicle.setPlateNumber("TEST-" + tag.toUpperCase());
        vehicleRepository.save(vehicle);
        premix = saveProduct("Premix " + tag);
        pigFeed = saveProduct("Cám heo " + tag);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM delivery_trip WHERE vehicle_id = ?", vehicle.getId());
            jdbcTemplate.execute("ALTER TABLE stock_transaction DISABLE TRIGGER trg_stock_transaction_immutable");
            jdbcTemplate.update("DELETE FROM stock_transaction WHERE warehouse_id = ?", warehouse.getId());
            jdbcTemplate.execute("ALTER TABLE stock_transaction ENABLE TRIGGER trg_stock_transaction_immutable");
            jdbcTemplate.update("DELETE FROM inventory WHERE warehouse_id = ?", warehouse.getId());
            jdbcTemplate.update("DELETE FROM stock_document_item WHERE document_id IN"
                    + " (SELECT id FROM stock_document WHERE customer_id = ?)", customer.getId());
            jdbcTemplate.update("DELETE FROM stock_document WHERE customer_id = ?", customer.getId());
            jdbcTemplate.update("DELETE FROM product WHERE category_id = ?", category.getId());
            jdbcTemplate.update("DELETE FROM category WHERE id = ?", category.getId());
            jdbcTemplate.update("DELETE FROM customer WHERE id = ?", customer.getId());
            jdbcTemplate.update("DELETE FROM warehouse WHERE id = ?", warehouse.getId());
            jdbcTemplate.update("DELETE FROM vehicle WHERE id = ?", vehicle.getId());
            jdbcTemplate.update("DELETE FROM employee WHERE id = ?", driver.getId());
        });
    }

    @Test
    @DisplayName("BR-DLV-06: " + THREADS + " luồng cùng xuất một chuyến - đúng 1 thành công, mỗi phiếu chỉ trừ tồn một lần")
    void concurrentDispatchShouldApplyOnce() throws Exception {
        setStock(pigFeed, 100);
        long first = createDraft(line(pigFeed, 10));
        long second = createDraft(line(pigFeed, 20));
        long tripId = createTrip(first, second);

        List<Throwable> results = runConcurrently(() -> tripService.dispatch(tripId, "test"));

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).allSatisfy(error -> assertThat(error)
                .isInstanceOf(BusinessException.class).hasMessageContaining("Đã xuất"));
        assertThat(stock(pigFeed)).isEqualTo(70);
        assertThat(transactionCount(first) + transactionCount(second)).isEqualTo(2);
    }

    @Test
    @DisplayName("BR-DLV-03: " + THREADS + " luồng cùng xếp một phiếu vào chuyến mới - đúng 1 thành công, phiếu chỉ ở một chuyến")
    void concurrentAssignShouldKeepOrderOnOneTrip() throws Exception {
        long order = createDraft(line(pigFeed, 10));

        List<Throwable> results = runConcurrently(() -> createTrip(order));

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).allSatisfy(error -> assertThat(error)
                .isInstanceOf(BusinessException.class).hasMessageContaining("chỉ nằm trên một chuyến"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM delivery_trip_stop WHERE document_id = ?",
                Integer.class, order)).isEqualTo(1);
    }

    @Test
    @DisplayName("Quy tắc cứng 1, 4: hai chuyến tranh 100 bao, mỗi chuyến cần 60 - đúng 1 xuất được; chuyến thua không trừ lẻ dòng nào")
    void competingTripsShouldNeverOversellOrPartiallyDeduct() throws Exception {
        setStock(premix, 1000);
        setStock(pigFeed, 100);
        long tripA = createTrip(createDraft(line(premix, 10)), createDraft(line(pigFeed, 60)));
        long tripB = createTrip(createDraft(line(premix, 10)), createDraft(line(pigFeed, 60)));

        List<Throwable> results = runConcurrently(List.of(
                () -> tripService.dispatch(tripA, "test"), () -> tripService.dispatch(tripB, "test")));

        assertThat(successes(results)).isEqualTo(1);
        assertThat(failures(results)).singleElement().satisfies(error -> assertThat(error)
                .isInstanceOf(BusinessException.class).hasMessageContaining("Không đủ tồn"));
        assertThat(stock(pigFeed)).isEqualTo(40);
        // Chuyến thua có thể đã trừ premix trước khi thiếu cám heo — rollback phải trả lại: chỉ trừ 10, không phải 20
        assertThat(stock(premix)).isEqualTo(990);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM delivery_trip WHERE id IN (?, ?) AND status = 'PLANNED'",
                Integer.class, tripA, tripB)).isEqualTo(1);
    }

    /**
     * Dựng đúng tình huống "trừ được dòng 1 rồi mới thiếu ở dòng 2" một cách chắc chắn (không phụ thuộc may rủi):
     * transaction T0 khóa dòng tồn cám heo → xuất chuyến trừ xong premix rồi phải CHỜ ở cám heo → T0 hạ tồn cám heo
     * xuống 50 và commit → UPDATE có điều kiện của xuất chuyến (cần 60) không còn đúng → lỗi → rollback cả premix.
     */
    @Test
    @DisplayName("Quy tắc cứng 4: đã trừ dòng premix rồi mới thiếu cám heo - rollback toàn bộ, premix không mất bao nào")
    void dispatchShouldRollBackDeductedLinesWhenLaterLineFails() throws Exception {
        setStock(premix, 1000);
        setStock(pigFeed, 100);
        long premixOrder = createDraft(line(premix, 10));
        long pigOrder = createDraft(line(pigFeed, 60));
        long tripId = createTrip(premixOrder, pigOrder);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch dispatchWaiting = new CountDownLatch(1);
        try {
            Future<?> blocker = pool.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject("SELECT quantity FROM inventory WHERE product_id = ? AND warehouse_id = ?"
                        + " FOR UPDATE", Integer.class, pigFeed.getId(), warehouse.getId());
                rowLocked.countDown();
                await(dispatchWaiting);
                jdbcTemplate.update("UPDATE inventory SET quantity = 50 WHERE product_id = ? AND warehouse_id = ?",
                        pigFeed.getId(), warehouse.getId());
            }));
            await(rowLocked);
            Future<Throwable> dispatch = pool.submit(() -> {
                try {
                    tripService.dispatch(tripId, "test");
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            });
            waitUntilSomeoneWaitsForLock();
            // Đang chờ khóa cám heo ⇒ premix (id nhỏ hơn) đã trừ xong trong transaction của xuất chuyến
            dispatchWaiting.countDown();
            blocker.get(30, TimeUnit.SECONDS);

            assertThat(dispatch.get(30, TimeUnit.SECONDS))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("Không đủ tồn");
        } finally {
            pool.shutdownNow();
        }

        assertThat(stock(premix)).isEqualTo(1000);
        assertThat(stock(pigFeed)).isEqualTo(50);
        assertThat(transactionCount(premixOrder) + transactionCount(pigOrder)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM stock_document WHERE id = ?", String.class, premixOrder))
                .isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM delivery_trip WHERE id = ?", String.class, tripId))
                .isEqualTo("PLANNED");
    }

    // ===== Helpers =====

    private long createTrip(Long... documentIds) {
        return tripService.create(new DeliveryTripRequest(LocalDate.now(), vehicle.getId(), driver.getId(), null,
                Arrays.asList(documentIds)), "test").id();
    }

    private long createDraft(SalesOrderLineRequest... lines) {
        return salesOrderService.create(new SalesOrderRequest(
                customer.getId(), warehouse.getId(), null, null, Arrays.asList(lines)), "test").id();
    }

    private static SalesOrderLineRequest line(Product product, int quantity) {
        return new SalesOrderLineRequest(product.getId(), quantity, new BigDecimal("1000"));
    }

    /** Chờ tới khi pg_stat_activity có một phiên đang đợi khóa dòng (tối đa 10 giây). */
    private void waitUntilSomeoneWaitsForLock() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Integer waiting = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_stat_activity"
                    + " WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Xuất chuyến không chờ khóa dòng tồn như dự kiến");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new AssertionError("Hết thời gian chờ");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

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
        product.setUnit(unitRepository.findByNameIgnoreCase("Bao").orElseThrow());
        product.setNetWeightKg(new BigDecimal("25"));
        return productRepository.save(product);
    }
}
