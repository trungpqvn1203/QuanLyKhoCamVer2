package com.grainwarehouse.product;

import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import com.grainwarehouse.supplier.entity.ProductSupplier;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.ProductSupplierRepository;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

import static com.grainwarehouse.product.CategoryApiTest.createdId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BR-PRD-10: chọn NCC ngay trong form thêm / sửa sản phẩm. Mỗi test rollback nhờ @Transactional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductSupplierSelectionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductUnitRepository unitRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductSupplierRepository productSupplierRepository;

    private Long categoryId;
    private Long bagId;
    private Supplier cuong;
    private Supplier dabaco;
    private Supplier greenfeed;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("Cám heo chọn NCC");
        categoryId = categoryRepository.save(category).getId();
        bagId = unitRepository.findByNameIgnoreCase("Bao").orElseThrow().getId();
        cuong = saveSupplier("NCC test Cường Phát", true);
        dabaco = saveSupplier("NCC test Dabaco", true);
        greenfeed = saveSupplier("NCC test Greenfeed", true);
    }

    @Test
    @DisplayName("BR-PRD-10: POST kèm supplierIds (gửi trùng) - 201, mỗi NCC một liên kết đang dùng")
    void shouldLinkSuppliersOnCreate() throws Exception {
        long id = createdId(create("Cám heo NCC", "[%d, %d, %d]".formatted(cuong.getId(), dabaco.getId(), cuong.getId()))
                .andExpect(status().isCreated()));

        assertThat(links(id)).containsOnly(entry(cuong.getId(), true), entry(dabaco.getId(), true));
    }

    @Test
    @DisplayName("BR-PRD-10: POST không gửi supplierIds - 201, chưa có NCC (tùy chọn)")
    void shouldCreateWithoutSuppliers() throws Exception {
        long id = createdId(create("Cám heo chưa NCC", null).andExpect(status().isCreated()));

        assertThat(links(id)).isEmpty();
    }

    @Test
    @DisplayName("BR-SUP-06: POST với NCC đã ngừng hoạt động - 409, kiểm tra trước khi lưu nên SP không được tạo")
    void shouldRejectInactiveSupplierAndRollback() throws Exception {
        Supplier stopped = saveSupplier("NCC test đã ngừng", false);
        long before = productRepository.count();

        create("Cám heo NCC ngừng", "[%d, %d]".formatted(cuong.getId(), stopped.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("POST với NCC không tồn tại - 404")
    void shouldRejectMissingSupplier() throws Exception {
        create("Cám heo NCC ma", "[999999]")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("BR-SUP-05: PUT đổi NCC - bỏ chọn thì gỡ (active=false), chọn lại thì bật lại đúng dòng cũ")
    void shouldSyncSuppliersOnUpdate() throws Exception {
        long id = createdId(create("Cám heo đổi NCC", "[%d, %d]".formatted(cuong.getId(), dabaco.getId())));

        update(id, "[%d, %d]".formatted(dabaco.getId(), greenfeed.getId())).andExpect(status().isOk());
        assertThat(links(id)).containsOnly(
                entry(cuong.getId(), false), entry(dabaco.getId(), true), entry(greenfeed.getId(), true));

        update(id, "[%d]".formatted(cuong.getId())).andExpect(status().isOk());
        assertThat(links(id)).containsOnly(
                entry(cuong.getId(), true), entry(dabaco.getId(), false), entry(greenfeed.getId(), false));
    }

    @Test
    @DisplayName("PUT không gửi supplierIds (như nút Mở bán lại) - giữ nguyên liên kết; gửi [] - gỡ hết")
    void shouldKeepLinksWhenSupplierIdsOmitted() throws Exception {
        long id = createdId(create("Cám heo giữ NCC", "[%d]".formatted(cuong.getId())));

        update(id, null).andExpect(status().isOk());
        assertThat(links(id)).containsOnly(entry(cuong.getId(), true));

        update(id, "[]").andExpect(status().isOk());
        assertThat(links(id)).containsOnly(entry(cuong.getId(), false));
    }

    @Test
    @DisplayName("BR-SUP-07: NCC đã gán nay ngừng hoạt động - PUT giữ NCC đó vẫn 200, chỉ chặn gán mới")
    void shouldKeepExistingLinkToStoppedSupplier() throws Exception {
        long id = createdId(create("Cám heo NCC sau ngừng", "[%d]".formatted(cuong.getId())));
        cuong.setActive(false);
        supplierRepository.saveAndFlush(cuong);

        update(id, "[%d]".formatted(cuong.getId())).andExpect(status().isOk());

        update(id, "[%d, %d]".formatted(cuong.getId(), dabaco.getId())).andExpect(status().isOk());
        assertThat(links(id)).containsOnly(entry(cuong.getId(), true), entry(dabaco.getId(), true));
    }

    private Supplier saveSupplier(String name, boolean active) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setActive(active);
        return supplierRepository.save(supplier);
    }

    private ResultActions create(String name, String supplierIdsJson) throws Exception {
        return mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                .content(productJson(name, supplierIdsJson, null)));
    }

    private ResultActions update(long id, String supplierIdsJson) throws Exception {
        String name = productRepository.findById(id).orElseThrow().getName();
        return mockMvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .content(productJson(name, supplierIdsJson, true)));
    }

    private String productJson(String name, String supplierIdsJson, Boolean active) {
        return """
                {"name": "%s", "categoryId": %d, "unitId": %d, "netWeightKg": 25%s%s}
                """.formatted(name, categoryId, bagId,
                supplierIdsJson == null ? "" : ", \"supplierIds\": " + supplierIdsJson,
                active == null ? "" : ", \"active\": " + active);
    }

    /** supplierId → liên kết đang dùng? (cả dòng đã gỡ) — query JPA tự flush nên thấy thay đổi chưa commit. */
    private Map<Long, Boolean> links(long productId) {
        return productSupplierRepository.findByIdProductId(productId).stream()
                .collect(Collectors.toMap(link -> link.getId().getSupplierId(), ProductSupplier::isActive));
    }
}
