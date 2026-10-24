package com.grainwarehouse.supplier;

import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test liên kết nhà cung cấp – sản phẩm (BR-SUP-05..07). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SupplierProductApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Supplier supplier;
    private Product pigFeed;
    private Product chickenFeed;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("Cám test");
        categoryRepository.save(category);
        pigFeed = saveProduct(category, "Cám heo con 25kg");
        chickenFeed = saveProduct(category, "Cám gà thịt 25kg");
        supplier = saveSupplier("Công ty CP Cám Việt");
        supplier.setTaxCode("0101234567");
    }

    @Test
    @DisplayName("BR-SUP-05: gán SP cho NCC - 201; danh sách SP của NCC chỉ có SP đã gán")
    void shouldLinkProductAndListIt() throws Exception {
        link(supplier.getId(), pigFeed.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.productId").value(pigFeed.getId()))
                .andExpect(jsonPath("$.data.productCode").value(pigFeed.getCode()))
                .andExpect(jsonPath("$.data.productName").value("Cám heo con 25kg"))
                .andExpect(jsonPath("$.data.unit").value("BAG"));

        mockMvc.perform(get("/api/suppliers/{id}/products", supplier.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(pigFeed.getId()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1));
    }

    @Test
    @DisplayName("BR-SUP-05: gán lại SP đang liên kết - 409 DUPLICATE_RESOURCE")
    void shouldRejectDuplicateLink() throws Exception {
        link(supplier.getId(), pigFeed.getId()).andExpect(status().isCreated());

        link(supplier.getId(), pigFeed.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("BR-SUP-05: gỡ liên kết - 204, dòng nối vẫn còn với active=false, SP biến khỏi danh sách")
    void shouldSoftUnlink() throws Exception {
        link(supplier.getId(), pigFeed.getId());
        link(supplier.getId(), chickenFeed.getId());

        mockMvc.perform(delete("/api/suppliers/{id}/products/{productId}", supplier.getId(), pigFeed.getId()))
                .andExpect(status().isNoContent());

        assertThat(linkActive(supplier.getId(), pigFeed.getId())).isFalse();
        mockMvc.perform(get("/api/suppliers/{id}/products", supplier.getId()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(chickenFeed.getId()));
    }

    @Test
    @DisplayName("BR-SUP-05: gán lại SP đã gỡ - bật lại chính dòng cũ, không tạo dòng mới")
    void shouldReactivateExistingLink() throws Exception {
        link(supplier.getId(), pigFeed.getId());
        mockMvc.perform(delete("/api/suppliers/{id}/products/{productId}", supplier.getId(), pigFeed.getId()));

        link(supplier.getId(), pigFeed.getId()).andExpect(status().isCreated());

        assertThat(linkActive(supplier.getId(), pigFeed.getId())).isTrue();
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM product_supplier WHERE supplier_id = ? AND product_id = ?",
                Integer.class, supplier.getId(), pigFeed.getId());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-SUP-05: gỡ SP chưa từng gán - 404 RESOURCE_NOT_FOUND")
    void shouldReturnNotFoundWhenUnlinkingUnknownLink() throws Exception {
        mockMvc.perform(delete("/api/suppliers/{id}/products/{productId}", supplier.getId(), pigFeed.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("BR-SUP-06: gán SP đã ngừng kinh doanh - 409 BUSINESS_RULE_VIOLATION")
    void shouldRejectInactiveProduct() throws Exception {
        pigFeed.setActive(false);
        productRepository.saveAndFlush(pigFeed);

        link(supplier.getId(), pigFeed.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("BR-SUP-06: gán SP cho NCC đã ngừng hoạt động - 409 BUSINESS_RULE_VIOLATION")
    void shouldRejectInactiveSupplier() throws Exception {
        mockMvc.perform(delete("/api/suppliers/{id}", supplier.getId())).andExpect(status().isNoContent());

        link(supplier.getId(), pigFeed.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("BR-SUP-07: ngừng hoạt động NCC - giữ nguyên các liên kết")
    void shouldKeepLinksWhenSupplierDeactivated() throws Exception {
        link(supplier.getId(), pigFeed.getId());

        mockMvc.perform(delete("/api/suppliers/{id}", supplier.getId())).andExpect(status().isNoContent());

        assertThat(linkActive(supplier.getId(), pigFeed.getId())).isTrue();
    }

    @Test
    @DisplayName("Gán SP không tồn tại - 404; thiếu productId - 400 VALIDATION_FAILED")
    void shouldValidateLinkRequest() throws Exception {
        link(supplier.getId(), 999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post("/api/suppliers/{id}/products", supplier.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("productId"));
    }

    @Test
    @DisplayName("BR-SUP-05: GET /api/products/{id}/suppliers - chỉ NCC đang liên kết, kèm mã/tên/người liên hệ")
    void shouldListSuppliersOfProduct() throws Exception {
        Supplier other = saveSupplier("Nhà máy Cám Nam");
        Supplier removed = saveSupplier("Đại lý Cám Bắc");
        link(supplier.getId(), pigFeed.getId());
        link(other.getId(), pigFeed.getId());
        link(removed.getId(), pigFeed.getId());
        link(supplier.getId(), chickenFeed.getId()); // SP khác — không được lẫn vào
        mockMvc.perform(delete("/api/suppliers/{id}/products/{productId}", removed.getId(), pigFeed.getId()));

        mockMvc.perform(get("/api/products/{id}/suppliers", pigFeed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].supplierId").value(supplier.getId()))
                .andExpect(jsonPath("$.data.items[0].supplierCode").value(supplier.getCode()))
                .andExpect(jsonPath("$.data.items[0].supplierName").value("Công ty CP Cám Việt"))
                .andExpect(jsonPath("$.data.items[0].taxCode").value("0101234567"))
                .andExpect(jsonPath("$.data.items[0].supplierActive").value(true))
                .andExpect(jsonPath("$.data.items[1].supplierId").value(other.getId()));
    }

    @Test
    @DisplayName("GET /api/products/{id}/suppliers - SP không tồn tại → 404")
    void shouldReturnNotFoundForUnknownProduct() throws Exception {
        mockMvc.perform(get("/api/products/{id}/suppliers", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private ResultActions link(Long supplierId, Long productId) throws Exception {
        return mockMvc.perform(post("/api/suppliers/{id}/products", supplierId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\": " + productId + "}"));
    }

    // JdbcTemplate đọc thẳng DB, không thấy thay đổi Hibernate còn giữ trong bộ nhớ.
    // Test rollback nên không có commit → phải flush tay trước khi đọc.
    private Boolean linkActive(Long supplierId, Long productId) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(
                "SELECT active FROM product_supplier WHERE supplier_id = ? AND product_id = ?",
                Boolean.class, supplierId, productId);
    }

    private Product saveProduct(Category category, String name) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setUnit(ProductUnit.BAG);
        product.setNetWeightKg(new BigDecimal("25"));
        return productRepository.save(product);
    }

    private Supplier saveSupplier(String name) {
        Supplier s = new Supplier();
        s.setName(name);
        return supplierRepository.save(s);
    }
}
