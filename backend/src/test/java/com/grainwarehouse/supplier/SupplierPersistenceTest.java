package com.grainwarehouse.supplier;

import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.SupplierRepository;
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
 * Kiểm chứng ràng buộc ở tầng DATABASE (migration V4), bỏ qua validation của Java
 * bằng cách INSERT thẳng qua JdbcTemplate.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SupplierPersistenceTest {

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("BR-SUP-02: save() xong đọc được ngay mã NCC do DB tự sinh (@Generated)")
    void shouldReadDbGeneratedCodeRightAfterSave() {
        Supplier supplier = new Supplier();
        supplier.setName("Công ty CP Cám Việt");
        supplier.setContactName("Nguyễn Văn A");

        Supplier saved = supplierRepository.save(supplier);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCode()).matches("NCC\\d{3,}");
    }

    @Test
    @DisplayName("BR-SUP-03: DB chặn tên NCC trùng dù khác hoa thường")
    void dbShouldRejectDuplicateNameIgnoringCase() {
        insertSupplier("Công ty CP Cám Việt", "A");

        assertThatThrownBy(() -> insertSupplier("CÔNG TY CP CÁM VIỆT", "B"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_supplier_name");
    }

    @Test
    @DisplayName("BR-SUP-01: DB chặn NCC thiếu người liên hệ")
    void dbShouldRejectMissingContactName() {
        assertThatThrownBy(() -> insertSupplier("Công ty CP Cám Việt", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("contact_name");
    }

    @Test
    @DisplayName("BR-SUP-05: DB chặn hai dòng nối trùng cặp (NCC, SP)")
    void dbShouldRejectDuplicateLink() {
        Long supplierId = insertSupplier("Công ty CP Cám Việt", "A");
        Long categoryId = jdbcTemplate.queryForObject(
                "INSERT INTO category (name) VALUES ('Cám test') RETURNING id", Long.class);
        Long productId = jdbcTemplate.queryForObject(
                "INSERT INTO product (name, category_id, unit, net_weight_kg) VALUES ('Cám heo', ?, 'BAG', 25)"
                        + " RETURNING id", Long.class, categoryId);
        insertLink(supplierId, productId);

        assertThatThrownBy(() -> insertLink(supplierId, productId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("pk_product_supplier");
    }

    @Test
    @DisplayName("BR-SUP-05: DB chặn liên kết tới SP không tồn tại (FK)")
    void dbShouldRejectLinkToMissingProduct() {
        Long supplierId = insertSupplier("Công ty CP Cám Việt", "A");

        assertThatThrownBy(() -> insertLink(supplierId, 999_999L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_product_supplier_product");
    }

    private Long insertSupplier(String name, String contactName) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO supplier (name, contact_name) VALUES (?, ?) RETURNING id",
                Long.class, name, contactName);
    }

    private void insertLink(Long supplierId, Long productId) {
        jdbcTemplate.update("INSERT INTO product_supplier (supplier_id, product_id) VALUES (?, ?)",
                supplierId, productId);
    }
}
