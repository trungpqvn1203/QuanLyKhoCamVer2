package com.grainwarehouse.sales;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ràng buộc của V13 và V25 (đơn khách đặt) ở tầng DB — tuyến phòng thủ cuối khi code Java quên kiểm tra.
 * Ghi thẳng SQL (không qua service) để chứng minh chính DB chặn.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SalesPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long warehouseId;
    private Long customerId;
    private Long documentId;

    @BeforeEach
    void setUp() {
        warehouseId = id("INSERT INTO warehouse (name, type, address) VALUES ('Kho test', 'CENTRAL', 'Hà Nội')"
                + " RETURNING id");
        customerId = id("INSERT INTO customer (name, type, phone) VALUES ('Khách test', 'DEALER', '0909999999')"
                + " RETURNING id");
        documentId = id("INSERT INTO stock_document (code, type, order_channel, status, warehouse_id, customer_id, document_date,"
                + " total_amount, completed_at) VALUES ('PX-TEST-1', 'OUTBOUND', 'DIRECT', 'COMPLETED', ?, ?, current_date,"
                + " 100000, now()) RETURNING id", warehouseId, customerId);
    }

    @Test
    @DisplayName("BR-SAL-01: DB chặn phiếu bán không có khách")
    void dbShouldRequireCustomerOnSalesOrder() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, order_channel, warehouse_id,"
                + " document_date) VALUES ('PX-TEST-2', 'OUTBOUND', 'DIRECT', ?, current_date)", warehouseId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_customer");
    }

    @Test
    @DisplayName("DB chặn phiếu nhập gắn khách hàng")
    void dbShouldRejectCustomerOnInboundReceipt() {
        Long supplierId = id("INSERT INTO supplier (name) VALUES ('NCC test') RETURNING id");

        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, warehouse_id,"
                + " supplier_id, customer_id, document_date) VALUES ('PN-TEST-2', 'INBOUND', ?, ?, ?, current_date)",
                warehouseId, supplierId, customerId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_customer");
    }

    @Test
    @DisplayName("BR-SAL-11: DB chặn đã thu vượt tổng phiếu")
    void dbShouldRejectOverpaidDocument() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE stock_document SET paid_amount = 100001 WHERE id = ?", documentId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_paid");
    }

    // Mỗi test chỉ kiểm MỘT vi phạm: sau lỗi đầu tiên PostgreSQL hủy transaction,
    // mọi câu sau đó báo "current transaction is aborted" thay vì lỗi ràng buộc cần kiểm

    @Test
    @DisplayName("BR-SAL-13: DB chặn sửa sale_payment (trigger)")
    void dbShouldRejectUpdatingPayment() {
        Long paymentId = insertPayment("PAYMENT", null);

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE sale_payment SET amount = 1 WHERE id = ?", paymentId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bất biến");
    }

    @Test
    @DisplayName("BR-SAL-13: DB chặn xóa sale_payment (trigger)")
    void dbShouldRejectDeletingPayment() {
        Long paymentId = insertPayment("PAYMENT", null);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM sale_payment WHERE id = ?", paymentId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bất biến");
    }

    @Test
    @DisplayName("BR-SAL-13: DB chặn hoàn một khoản thu hai lần")
    void dbShouldRejectDoubleReversal() {
        Long paymentId = insertPayment("PAYMENT", null);
        insertPayment("REVERSAL", paymentId);

        assertThatThrownBy(() -> insertPayment("REVERSAL", paymentId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_sale_payment_reversal_of");
    }

    @Test
    @DisplayName("BR-SAL-13: DB chặn dòng hoàn không trỏ về khoản gốc")
    void dbShouldRejectReversalWithoutOriginal() {
        assertThatThrownBy(() -> insertPayment("REVERSAL", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_sale_payment_reversal");
    }

    @Test
    @DisplayName("BR-SAL-06: mỗi cặp (khách, SP) chỉ có một giá riêng")
    void dbShouldRejectDuplicateCustomerPrice() {
        Long categoryId = id("INSERT INTO category (name) VALUES ('Cám test') RETURNING id");
        Long productId = id("INSERT INTO product (name, category_id, unit_id, net_weight_kg)"
                + " VALUES ('Cám heo', ?, (SELECT id FROM product_unit WHERE name = 'Bao'), 25) RETURNING id", categoryId);
        String insert = "INSERT INTO customer_product_price (customer_id, product_id, unit_price) VALUES (?, ?, 1000)";
        jdbcTemplate.update(insert, customerId, productId);

        assertThatThrownBy(() -> jdbcTemplate.update(insert, customerId, productId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_customer_product_price");
    }

    // ===== V25: đơn khách đặt (Chờ xác nhận) =====

    @Test
    @DisplayName("BR-SAL-17: DB cho phiếu bán Chờ xác nhận không có kho")
    void dbShouldAllowPendingSaleWithoutWarehouse() {
        assertThat(insertSale("PX-TEST-3", "PENDING", null, "PHONE")).isPositive();
    }

    @Test
    @DisplayName("BR-SAL-16: DB chặn phiếu bán Nháp không có kho — kho bắt buộc từ Nháp trở đi")
    void dbShouldRequireWarehouseFromDraft() {
        assertThatThrownBy(() -> insertSale("PX-TEST-3", "DRAFT", null, "DIRECT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_warehouse");
    }

    @Test
    @DisplayName("BR-SAL-16: DB chặn phiếu đã xác nhận rồi hủy mà không có kho")
    void dbShouldRequireWarehouseOnCancelledConfirmedSale() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, order_channel, status,"
                + " customer_id, document_date, confirmed_at, confirmed_by, cancelled_at)"
                + " VALUES ('PX-TEST-3', 'OUTBOUND', 'PHONE', 'CANCELLED', ?, current_date, now(), 'test', now())",
                customerId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_warehouse");
    }

    @Test
    @DisplayName("BR-SAL-16: DB chặn trạng thái Chờ xác nhận ở chứng từ không phải phiếu bán")
    void dbShouldRejectPendingOnInboundReceipt() {
        Long supplierId = id("INSERT INTO supplier (name) VALUES ('NCC test') RETURNING id");

        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, status, warehouse_id,"
                + " supplier_id, document_date) VALUES ('PN-TEST-3', 'INBOUND', 'PENDING', ?, ?, current_date)",
                warehouseId, supplierId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_pending");
    }

    // Mỗi ca chỉ MỘT câu lệnh lỗi: PostgreSQL hủy cả transaction test sau lỗi đầu tiên (SQL state 25P02)

    @Test
    @DisplayName("BR-SAL-17: DB bắt buộc phiếu bán có kênh nhận đơn")
    void dbShouldRequireOrderChannelOnSale() {
        assertThatThrownBy(() -> insertSale("PX-TEST-3", "DRAFT", warehouseId, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_order_channel");
    }

    @Test
    @DisplayName("BR-SAL-17: DB chặn kênh nhận đơn ngoài danh sách")
    void dbShouldRejectUnknownOrderChannel() {
        assertThatThrownBy(() -> insertSale("PX-TEST-3", "DRAFT", warehouseId, "FACEBOOK"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_order_channel");
    }

    @Test
    @DisplayName("BR-SAL-17: DB chặn SL khách đặt <= 0")
    void dbShouldRejectNonPositiveRequestQuantity() {
        Long pendingId = insertSale("PX-TEST-3", "PENDING", null, "ZALO");
        Long productId = insertProduct();

        assertThatThrownBy(() -> insertRequestItem(pendingId, productId, 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_sale_request_item_quantity");
    }

    @Test
    @DisplayName("BR-SAL-17: DB chặn SP lặp trong phần khách đặt")
    void dbShouldRejectDuplicateRequestProduct() {
        Long pendingId = insertSale("PX-TEST-3", "PENDING", null, "ZALO");
        Long productId = insertProduct();
        insertRequestItem(pendingId, productId, 20);

        assertThatThrownBy(() -> insertRequestItem(pendingId, productId, 5))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_sale_request_item_product");
    }

    private void insertRequestItem(Long documentId, Long productId, int quantity) {
        jdbcTemplate.update("INSERT INTO sale_request_item (document_id, product_id, quantity) VALUES (?, ?, ?)",
                documentId, productId, quantity);
    }

    private Long insertSale(String code, String status, Long warehouse, String channel) {
        return id("INSERT INTO stock_document (code, type, order_channel, status, warehouse_id, customer_id,"
                + " document_date) VALUES (?, 'OUTBOUND', ?, ?, ?, ?, current_date) RETURNING id",
                code, channel, status, warehouse, customerId);
    }

    private Long insertProduct() {
        Long categoryId = id("INSERT INTO category (name) VALUES ('Cám test V25') RETURNING id");
        return id("INSERT INTO product (name, category_id, unit_id, net_weight_kg)"
                + " VALUES ('Cám gà', ?, (SELECT id FROM product_unit WHERE name = 'Bao'), 25) RETURNING id", categoryId);
    }

    private Long insertPayment(String type, Long reversalOfId) {
        return id("INSERT INTO sale_payment (document_id, type, method, amount, payment_date, reversal_of_id)"
                + " VALUES (?, ?, 'CASH', 1000, current_date, ?) RETURNING id", documentId, type, reversalOfId);
    }

    private Long id(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
