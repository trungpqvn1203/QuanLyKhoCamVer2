package com.grainwarehouse.inventory;

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
 * Kiểm chứng ràng buộc ở tầng DATABASE (migration V6), bỏ qua code Java bằng cách ghi thẳng qua JdbcTemplate.
 * Đây là tuyến phòng thủ cuối: dù service có lỗi, DB vẫn không cho tồn âm hay sửa lịch sử.
 * Mỗi test chỉ gây MỘT lỗi SQL ở cuối: sau lỗi, PostgreSQL hủy transaction, câu lệnh tiếp theo đều fail.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long productId;
    private Long warehouseId;
    private Long supplierId;
    private Long itemId;

    @BeforeEach
    void setUp() {
        Long categoryId = id("INSERT INTO category (name) VALUES ('Cám test') RETURNING id");
        productId = id("INSERT INTO product (name, category_id, unit, net_weight_kg) VALUES ('Cám heo', ?, 'BAG', 25)"
                + " RETURNING id", categoryId);
        warehouseId = id("INSERT INTO warehouse (name, type, address) VALUES ('Kho test', 'CENTRAL', 'Hà Nội')"
                + " RETURNING id");
        supplierId = id("INSERT INTO supplier (name, contact_name) VALUES ('NCC test', 'A') RETURNING id");
        Long documentId = id("INSERT INTO stock_document (code, type, warehouse_id, supplier_id, document_date)"
                + " VALUES ('PN-TEST-1', 'INBOUND', ?, ?, current_date) RETURNING id", warehouseId, supplierId);
        itemId = id("INSERT INTO stock_document_item (document_id, product_id, ordered_quantity, quantity,"
                + " unit_price, line_amount) VALUES (?, ?, 10, 10, 1000, 10000) RETURNING id", documentId, productId);
    }

    @Test
    @DisplayName("BR-INV-03: DB chặn tồn âm (CHECK quantity >= 0), kể cả khi code Java quên kiểm tra")
    void dbShouldRejectNegativeInventory() {
        jdbcTemplate.update("INSERT INTO inventory (product_id, warehouse_id, quantity) VALUES (?, ?, 5)",
                productId, warehouseId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE inventory SET quantity = quantity - 6 WHERE product_id = ? AND warehouse_id = ?",
                productId, warehouseId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_inventory_quantity_non_negative");
    }

    @Test
    @DisplayName("BR-INV-A01: mỗi cặp (SP, kho) chỉ có một dòng tồn")
    void dbShouldRejectDuplicateInventoryRow() {
        jdbcTemplate.update("INSERT INTO inventory (product_id, warehouse_id, quantity) VALUES (?, ?, 5)",
                productId, warehouseId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO inventory (product_id, warehouse_id, quantity) VALUES (?, ?, 1)", productId, warehouseId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_inventory_product_warehouse");
    }

    @Test
    @DisplayName("BR-INV-05: DB chặn UPDATE stock_transaction (trigger)")
    void dbShouldRejectUpdatingStockTransaction() {
        Long transactionId = insertTransaction("IN", 10, 0, 10, null);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE stock_transaction SET quantity = 99 WHERE id = ?", transactionId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bất biến");
    }

    @Test
    @DisplayName("BR-INV-05: DB chặn DELETE stock_transaction (trigger)")
    void dbShouldRejectDeletingStockTransaction() {
        Long transactionId = insertTransaction("IN", 10, 0, 10, null);

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM stock_transaction WHERE id = ?", transactionId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bất biến");
    }

    @Test
    @DisplayName("BR-INV-05: DB chặn TRUNCATE stock_transaction (trigger mức câu lệnh)")
    void dbShouldRejectTruncatingStockTransaction() {
        assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE stock_transaction CASCADE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("bất biến");
    }

    @Test
    @DisplayName("Giao dịch phải cân: IN thì after = before + quantity")
    void dbShouldRejectUnbalancedTransaction() {
        assertThatThrownBy(() -> insertTransaction("IN", 10, 0, 9, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_transaction_balance");
    }

    @Test
    @DisplayName("BR-IMP-12: một giao dịch chỉ bị đảo một lần (UNIQUE reversal_of_id)")
    void dbShouldRejectSecondReversalOfSameTransaction() {
        Long original = insertTransaction("IN", 10, 0, 10, null);
        insertTransaction("OUT", 10, 10, 0, original);

        assertThatThrownBy(() -> insertTransaction("OUT", 10, 10, 0, original))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_stock_transaction_reversal_of");
    }

    @Test
    @DisplayName("Phiếu nhập bắt buộc có nhà cung cấp")
    void dbShouldRejectInboundWithoutSupplier() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, warehouse_id,"
                + " document_date) VALUES ('PN-TEST-2', 'INBOUND', ?, current_date)", warehouseId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_supplier");
    }

    @Test
    @DisplayName("Trạng thái phiếu chỉ nhận DRAFT/COMPLETED/CANCELLED/REVERSED")
    void dbShouldRejectUnknownStatus() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, status, warehouse_id,"
                + " supplier_id, document_date) VALUES ('PN-TEST-3', 'INBOUND', 'APPROVED', ?, ?, current_date)",
                warehouseId, supplierId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_status");
    }

    @Test
    @DisplayName("Phiếu Hoàn tất phải có completed_at")
    void dbShouldRejectCompletedWithoutTimestamp() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO stock_document (code, type, status, warehouse_id,"
                + " supplier_id, document_date) VALUES ('PN-TEST-4', 'INBOUND', 'COMPLETED', ?, ?, current_date)",
                warehouseId, supplierId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_completed_at");
    }

    @Test
    @DisplayName("SL thực nhận trên dòng phiếu không được âm")
    void dbShouldRejectNegativeItemQuantity() {
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE stock_document_item SET quantity = -1 WHERE id = ?",
                itemId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_stock_document_item_quantity");
    }

    private Long insertTransaction(String type, int quantity, int before, int after, Long reversalOf) {
        return id("INSERT INTO stock_transaction (document_item_id, product_id, warehouse_id, type, quantity,"
                        + " quantity_before, quantity_after, reversal_of_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                itemId, productId, warehouseId, type, quantity, before, after, reversalOf);
    }

    private Long id(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
