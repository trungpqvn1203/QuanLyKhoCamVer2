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

        Supplier saved = supplierRepository.save(supplier);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCode()).matches("NCC\\d{3,}");
    }

    @Test
    @DisplayName("BR-SUP-03: DB chặn tên NCC trùng dù khác hoa thường")
    void dbShouldRejectDuplicateNameIgnoringCase() {
        insertSupplier("Công ty CP Cám Việt", null);

        assertThatThrownBy(() -> insertSupplier("CÔNG TY CP CÁM VIỆT", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_supplier_name");
    }

    @Test
    @DisplayName("BR-SUP-09: DB chặn MST sai định dạng (chưa chuẩn hóa)")
    void dbShouldRejectMalformedTaxCode() {
        assertThatThrownBy(() -> insertSupplier("Công ty CP Cám Việt", "0101 234 567"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_supplier_tax_code");
    }

    @Test
    @DisplayName("BR-SUP-09: DB chặn MST trùng; nhiều NCC không có MST vẫn hợp lệ")
    void dbShouldRejectDuplicateTaxCode() {
        insertSupplier("Nhà máy A", null);
        insertSupplier("Nhà máy B", null);
        insertSupplier("Công ty CP Cám Việt", "0101234567-001");

        assertThatThrownBy(() -> insertSupplier("Công ty khác", "0101234567-001"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_supplier_tax_code");
    }

    @Test
    @DisplayName("BR-SUP-05: DB chặn hai dòng nối trùng cặp (NCC, SP)")
    void dbShouldRejectDuplicateLink() {
        Long supplierId = insertSupplier("Công ty CP Cám Việt", null);
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
        Long supplierId = insertSupplier("Công ty CP Cám Việt", null);

        assertThatThrownBy(() -> insertLink(supplierId, 999_999L))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_product_supplier_product");
    }

    private Long insertSupplier(String name, String taxCode) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO supplier (name, tax_code) VALUES (?, ?) RETURNING id",
                Long.class, name, taxCode);
    }

    private void insertLink(Long supplierId, Long productId) {
        jdbcTemplate.update("INSERT INTO product_supplier (supplier_id, product_id) VALUES (?, ?)",
                supplierId, productId);
    }
}
