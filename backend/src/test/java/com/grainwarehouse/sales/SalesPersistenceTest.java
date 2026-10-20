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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ràng buộc của V13 ở tầng DB — tuyến phòng thủ cuối khi code Java quên kiểm tra.
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
        documentId = id("INSERT INTO stock_document (code, type, status, warehouse_id, customer_id, document_date,"
                + " total_amount, completed_at) VALUES ('PX-TEST-1', 'OUTBOUND', 'COMPLETED', ?, ?, current_date,"
                + " 100000, now()) RETURNING id", warehouseId, customerId);
    }

    @Test
    @DisplayName("BR-SAL-01: DB chặn phiếu bán không có khách")
    void dbShouldRequireCustomerOnSalesOrder() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, warehouse_id,"
                + " document_date) VALUES ('PX-TEST-2', 'OUTBOUND', ?, current_date)", warehouseId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_customer");
    }

    @Test
    @DisplayName("DB chặn phiếu nhập gắn khách hàng")
    void dbShouldRejectCustomerOnInboundReceipt() {
        Long supplierId = id("INSERT INTO supplier (name, contact_name) VALUES ('NCC test', 'A') RETURNING id");

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
        Long productId = id("INSERT INTO product (name, category_id, unit, net_weight_kg)"
                + " VALUES ('Cám heo', ?, 'BAG', 25) RETURNING id", categoryId);
        String insert = "INSERT INTO customer_product_price (customer_id, product_id, unit_price) VALUES (?, ?, 1000)";
        jdbcTemplate.update(insert, customerId, productId);

        assertThatThrownBy(() -> jdbcTemplate.update(insert, customerId, productId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_customer_product_price");
    }

    private Long insertPayment(String type, Long reversalOfId) {
        return id("INSERT INTO sale_payment (document_id, type, method, amount, payment_date, reversal_of_id)"
                + " VALUES (?, ?, 'CASH', 1000, current_date, ?) RETURNING id", documentId, type, reversalOfId);
    }

    private Long id(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
