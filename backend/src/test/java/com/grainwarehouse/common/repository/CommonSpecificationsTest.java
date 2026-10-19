package com.grainwarehouse.common.repository;

import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm chứng các điều kiện lọc dùng chung trên PostgreSQL thật (hàm immutable_unaccent ở migration V5).
 * Dùng Product làm entity mẫu; mọi truy vấn giới hạn trong danh mục riêng của test để không lẫn dữ liệu khác.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommonSpecificationsTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Danh mục test lọc");
        category = categoryRepository.save(category);
        save("Cám heo con 25kg", ProductUnit.BAG, "25");
        save("CÁM HEO THỊT 40KG", ProductUnit.BAG, "40");
        save("Cám gà Đồng Nai", ProductUnit.BAG, "20");
        save("Cám xá 100% ngô", ProductUnit.KG, "1");
    }

    @Test
    @DisplayName("V5: immutable_unaccent bỏ dấu tiếng Việt, kể cả chữ đ/Đ")
    void shouldStripVietnameseAccents() {
        String result = jdbcTemplate.queryForObject("SELECT immutable_unaccent('Cám gà Đồng Nai đỏ')", String.class);

        assertThat(result).isEqualTo("Cam ga Dong Nai do");
    }

    @Test
    @DisplayName("keywordContains: gõ không dấu, khác hoa thường vẫn khớp")
    void shouldMatchWithoutAccentsAndCase() {
        assertThat(names(CommonSpecifications.keywordContains("  cam heo ", "code", "name")))
                .containsExactlyInAnyOrder("Cám heo con 25kg", "CÁM HEO THỊT 40KG");
        assertThat(names(CommonSpecifications.keywordContains("dong nai", "code", "name")))
                .containsExactly("Cám gà Đồng Nai");
        // Gõ có dấu cũng khớp bản ghi (hai vế cùng được chuẩn hóa)
        assertThat(names(CommonSpecifications.keywordContains("THỊT", "code", "name")))
                .containsExactly("CÁM HEO THỊT 40KG");
    }

    @Test
    @DisplayName("keywordContains: % và _ là ký tự thường, không phải ký tự đại diện")
    void shouldEscapeLikeWildcards() {
        assertThat(names(CommonSpecifications.keywordContains("100%", "code", "name")))
                .containsExactly("Cám xá 100% ngô");
        assertThat(names(CommonSpecifications.keywordContains("_", "code", "name"))).isEmpty();
    }

    @Test
    @DisplayName("in / between: lọc theo danh sách và khoảng, hai đầu tính, thiếu một đầu là khoảng hở")
    void shouldFilterByInAndBetween() {
        assertThat(names(CommonSpecifications.in("unit", List.of(ProductUnit.KG))))
                .containsExactly("Cám xá 100% ngô");
        assertThat(names(CommonSpecifications.between("netWeightKg", new BigDecimal("20"), new BigDecimal("25"))))
                .containsExactlyInAnyOrder("Cám heo con 25kg", "Cám gà Đồng Nai");
        assertThat(names(CommonSpecifications.between("netWeightKg", new BigDecimal("25"), null)))
                .containsExactlyInAnyOrder("Cám heo con 25kg", "CÁM HEO THỊT 40KG");
    }

    @Test
    @DisplayName("Tham số rỗng → không lọc (trả null), không phải 'không khớp gì'")
    void shouldIgnoreEmptyParameters() {
        assertThat(CommonSpecifications.<Product>keywordContains("   ", "name")).isNull();
        assertThat(CommonSpecifications.<Product>equal("active", null)).isNull();
        assertThat(CommonSpecifications.<Product>in("unit", List.of())).isNull();
        assertThat(CommonSpecifications.<Product, BigDecimal>between("netWeightKg", null, null)).isNull();
        assertThat(names(null)).hasSize(4);
    }

    private void save(String name, ProductUnit unit, String netWeightKg) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setUnit(unit);
        product.setNetWeightKg(new BigDecimal(netWeightKg));
        productRepository.save(product);
    }

    // Luôn kèm điều kiện danh mục của test → kết quả không phụ thuộc dữ liệu có sẵn trong DB test
    private List<String> names(Specification<Product> spec) {
        Specification<Product> scoped = Specification.allOf(
                CommonSpecifications.equal("category.id", category.getId()), spec);
        return productRepository.findAll(scoped).stream().map(Product::getName).toList();
    }
}
