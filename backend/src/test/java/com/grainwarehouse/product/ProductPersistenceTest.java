package com.grainwarehouse.product;

import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kiểm chứng ràng buộc ở tầng DATABASE (migration V2), bỏ qua validation của Java
 * bằng cách INSERT thẳng qua JdbcTemplate. Đây là tuyến phòng thủ cuối cùng.
 * {@code @Transactional}: mỗi test tự rollback, DB test luôn sạch.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductPersistenceTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductUnitRepository unitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Cám heo");
        category = categoryRepository.save(category);
    }

    @Test
    @DisplayName("BR-PRD-02: save() xong đọc được ngay mã SP do DB tự sinh (@Generated)")
    void shouldReadDbGeneratedCodeRightAfterSave() {
        Product product = new Product();
        product.setName("Cám heo con 25kg");
        product.setCategory(category);
        product.setUnit(unitRepository.findByNameIgnoreCase("Bao").orElseThrow());
        product.setNetWeightKg(new BigDecimal("25"));

        Product saved = productRepository.save(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCode()).matches("SP\\d{6}");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("BR-PRD-03: DB chặn tên sản phẩm trùng dù khác hoa thường")
    void dbShouldRejectDuplicateNameIgnoringCase() {
        insertProduct("Cám heo con 25kg", "Bao", "25");

        assertThatThrownBy(() -> insertProduct("CÁM HEO CON 25KG", "Bao", "25"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_product_name");
    }

    @Test
    @DisplayName("BR-PRD-05: DB chặn đơn vị không có trong danh mục đơn vị (FK)")
    void dbShouldRejectUnknownUnit() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO product (name, category_id, unit_id, net_weight_kg) VALUES ('Cám gà đẻ', ?, -1, 1000)",
                category.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_product_unit");
    }

    @Test
    @DisplayName("BR-UNIT-01: DB chặn tên đơn vị trùng dù khác hoa thường")
    void dbShouldRejectDuplicateUnitNameIgnoringCase() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO product_unit (name) VALUES ('BAO')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_product_unit_name");
    }

    @Test
    @DisplayName("V23: chuyển dữ liệu - có sẵn Bao (khối lượng tùy SP) và Kg (cố định 1 kg)")
    void shouldSeedLegacyUnits() {
        assertThat(unitRepository.findByNameIgnoreCase("Bao").orElseThrow().getFixedNetWeightKg()).isNull();
        assertThat(unitRepository.findByNameIgnoreCase("Kg").orElseThrow().getFixedNetWeightKg())
                .isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("BR-PRD-06: DB chặn khối lượng tịnh <= 0")
    void dbShouldRejectNonPositiveNetWeight() {
        assertThatThrownBy(() -> insertProduct("Cám cá xá", "Kg", "0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_product_net_weight");
    }

    @Test
    @DisplayName("BR-CAT-02: DB chặn tên danh mục trùng dù khác hoa thường")
    void dbShouldRejectDuplicateCategoryNameIgnoringCase() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO category (name) VALUES ('CÁM HEO')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_category_name");
    }

    @Test
    @DisplayName("BR-STK-03: DB chặn tồn tối thiểu âm (V7)")
    void dbShouldRejectNegativeMinStock() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO product (name, category_id, unit_id, net_weight_kg,"
                + " min_stock_quantity) VALUES ('Cám heo', ?, (SELECT id FROM product_unit WHERE name = 'Bao'), 25, -1)", category.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_product_min_stock");
    }

    private void insertProduct(String name, String unitName, String netWeightKg) {
        jdbcTemplate.update("INSERT INTO product (name, category_id, unit_id, net_weight_kg)"
                        + " VALUES (?, ?, (SELECT id FROM product_unit WHERE name = ?), ?)",
                name, category.getId(), unitName, new BigDecimal(netWeightKg));
    }
}
