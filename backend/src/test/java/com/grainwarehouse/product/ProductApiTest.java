package com.grainwarehouse.product;

import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
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

import static com.grainwarehouse.product.CategoryApiTest.createdId;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test API sản phẩm trên DB thật; mỗi test rollback nhờ @Transactional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductUnitRepository unitRepository;

    private Long categoryId;
    // Đơn vị có sẵn từ V23: Bao (khối lượng tùy SP), Kg (cố định 1 kg)
    private Long bagId;
    private Long kgId;

    @BeforeEach
    void setUp() {
        categoryId = saveCategory("Cám heo", true).getId();
        bagId = unitRepository.findByNameIgnoreCase("Bao").orElseThrow().getId();
        kgId = unitRepository.findByNameIgnoreCase("Kg").orElseThrow().getId();
    }

    @Test
    @DisplayName("BR-PRD-02: POST - 201, mã SP tự sinh, kèm tên danh mục")
    void shouldCreateProductWithGeneratedCode() throws Exception {
        createProduct(productJson("Cám heo con 25kg", bagId, "25"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value(matchesPattern("SP\\d{6}")))
                .andExpect(jsonPath("$.data.categoryName").value("Cám heo"))
                .andExpect(jsonPath("$.data.unitId").value(bagId))
                .andExpect(jsonPath("$.data.unitName").value("Bao"))
                .andExpect(jsonPath("$.data.netWeightKg").value(25))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("POST thiếu tên, khối lượng âm - 400 VALIDATION_FAILED, details đủ field")
    void shouldRejectInvalidFields() throws Exception {
        createProduct("{\"categoryId\": " + categoryId + ", \"unitId\": " + bagId + ", \"netWeightKg\": -5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[*].field").value(hasItems("name", "netWeightKg")));
    }

    @Test
    @DisplayName("BR-PRD-05: POST thiếu unitId - 400 tại field unitId; unitId không tồn tại - 404")
    void shouldRejectMissingOrUnknownUnit() throws Exception {
        createProduct("""
                {"name": "Cám gà đẻ", "categoryId": %d, "netWeightKg": 25}
                """.formatted(categoryId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("unitId"));

        createProduct(productJson("Cám gà đẻ", 999_999L, "25"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("BR-UNIT-01: đơn vị người dùng tự thêm (Túi) - khối lượng tùy SP, response có tên đơn vị")
    void shouldCreateProductWithCustomUnit() throws Exception {
        Long bagSmallId = saveUnit("Túi", true).getId();

        createProduct(productJson("Cám cá túi 5kg", bagSmallId, "5"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unitName").value("Túi"))
                .andExpect(jsonPath("$.data.netWeightKg").value(5));
    }

    @Test
    @DisplayName("BR-UNIT-02: tạo SP với đơn vị đã ngừng - 409; SP cũ giữ đơn vị đó vẫn sửa được")
    void shouldRejectInactiveUnitOnlyWhenSwitching() throws Exception {
        ProductUnit box = saveUnit("Thùng", true);
        long id = createdId(createProduct(productJson("Cám cá thùng", box.getId(), "20")));
        box.setActive(false);
        unitRepository.saveAndFlush(box);

        createProduct(productJson("Cám cá thùng mới", box.getId(), "20"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        mockMvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Cám cá thùng", "categoryId": %d, "unitId": %d, "netWeightKg": 20,
                         "description": "Sửa mô tả", "active": true}
                        """.formatted(categoryId, box.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unitName").value("Thùng"));
    }

    @Test
    @DisplayName("BR-PRD-03: POST tên trùng (khác hoa thường) - 409 DUPLICATE_RESOURCE")
    void shouldRejectDuplicateName() throws Exception {
        createProduct(productJson("Cám heo con 25kg", bagId, "25")).andExpect(status().isCreated());

        createProduct(productJson("cám heo con 25KG", bagId, "25"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("BR-PRD-09: tạo lại tên của sản phẩm đã ngừng kinh doanh - 409, gợi ý mở bán lại kèm mã SP")
    void shouldRejectRecreatingDeactivatedProduct() throws Exception {
        ResultActions created = createProduct(productJson("Cám lợn C14", bagId, "25"));
        long id = createdId(created);
        String code = com.jayway.jsonpath.JsonPath.read(
                created.andReturn().getResponse().getContentAsString(), "$.data.code");
        mockMvc.perform(delete("/api/products/{id}", id)).andExpect(status().isNoContent());

        createProduct(productJson("cám lợn c14", bagId, "25"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.error.message").value(
                        "Sản phẩm 'Cám lợn C14' (" + code + ") đã tồn tại nhưng đang ngừng kinh doanh."
                                + " Hãy mở bán lại thay vì tạo mới"));
    }

    @Test
    @DisplayName("BR-UNIT-03: đơn vị Kg (cố định 1 kg) với khối lượng 25 - 409; với 1.000 - 201 (so sánh BigDecimal)")
    void shouldRequireOneKgForKgUnit() throws Exception {
        createProduct(productJson("Cám cá xá", kgId, "25"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.error.message").value("Sản phẩm đơn vị Kg phải có khối lượng tịnh bằng 1 kg"));

        createProduct(productJson("Cám cá xá", kgId, "1.000"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("BR-UNIT-03: PUT đổi SP sang đơn vị Kg mà giữ khối lượng 25 - 409")
    void shouldCheckFixedWeightWhenSwitchingUnit() throws Exception {
        long id = createdId(createProduct(productJson("Cám heo con 25kg", bagId, "25")));

        mockMvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Cám heo con 25kg", "categoryId": %d, "unitId": %d, "netWeightKg": 25, "active": true}
                        """.formatted(categoryId, kgId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("POST vào danh mục đã ngừng dùng - 409 BUSINESS_RULE_VIOLATION")
    void shouldRejectInactiveCategory() throws Exception {
        Long inactiveId = saveCategory("Cám vịt", false).getId();

        createProduct(productJsonWithCategory("Cám vịt con", inactiveId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    @DisplayName("POST danh mục không tồn tại - 404 RESOURCE_NOT_FOUND")
    void shouldRejectMissingCategory() throws Exception {
        createProduct(productJsonWithCategory("Cám vịt con", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("BR-PRD-02: PUT gửi kèm code - code bị bỏ qua, không đổi")
    void shouldNotChangeCodeOnUpdate() throws Exception {
        ResultActions created = createProduct(productJson("Cám heo con 25kg", bagId, "25"));
        long id = createdId(created);
        String originalCode = com.jayway.jsonpath.JsonPath.read(
                created.andReturn().getResponse().getContentAsString(), "$.data.code");

        mockMvc.perform(put("/api/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "HACK01", "name": "Cám heo thịt 25kg", "categoryId": %d,
                                 "unitId": %d, "netWeightKg": 25, "active": true}
                                """.formatted(categoryId, bagId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Cám heo thịt 25kg"))
                .andExpect(jsonPath("$.data.code").value(originalCode));
    }

    @Test
    @DisplayName("BR-PRD-08: DELETE - 204, sản phẩm vẫn còn nhưng active=false")
    void shouldSoftDelete() throws Exception {
        long id = createdId(createProduct(productJson("Cám heo con 25kg", bagId, "25")));

        mockMvc.perform(delete("/api/products/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("GET danh sách - cấu trúc data.items + data.pagination theo contract-spec")
    void shouldReturnPaginatedList() throws Exception {
        createProduct(productJson("Cám heo con 25kg", bagId, "25"));
        createProduct(productJson("Cám heo thịt 25kg", bagId, "25"));

        mockMvc.perform(get("/api/products").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.pagination.page").value(0))
                .andExpect(jsonPath("$.data.pagination.size").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.data.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.data.pagination.isFirst").value(true))
                .andExpect(jsonPath("$.data.pagination.isLast").value(false));
    }

    @Test
    @DisplayName("GET ?keyword - tìm theo tên, không phân biệt hoa thường; totalElements theo kết quả lọc")
    void shouldSearchByName() throws Exception {
        createProduct(productJson("Cám heo con 25kg", bagId, "25"));
        createProduct(productJson("Cám heo thịt 40kg", bagId, "40"));
        createProduct(productJson("Cám gà đẻ 40kg", bagId, "40"));

        mockMvc.perform(get("/api/products").param("keyword", "  HEO  ").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.data.pagination.totalPages").value(2));
    }

    @Test
    @DisplayName("GET ?keyword - tìm theo mã sản phẩm")
    void shouldSearchByCode() throws Exception {
        String code = com.jayway.jsonpath.JsonPath.read(
                createProduct(productJson("Cám heo con 25kg", bagId, "25"))
                        .andReturn().getResponse().getContentAsString(), "$.data.code");
        createProduct(productJson("Cám gà đẻ 40kg", bagId, "40"));

        mockMvc.perform(get("/api/products").param("keyword", code.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value(code));
    }

    @Test
    @DisplayName("GET ?keyword=% - ký tự đại diện LIKE được hiểu là chữ thường, không khớp tất cả")
    void shouldEscapeLikeWildcards() throws Exception {
        createProduct(productJson("Cám heo con 25kg", bagId, "25"));

        mockMvc.perform(get("/api/products").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.totalElements").value(0));
    }

    @Test
    @DisplayName("GET ?categoryId&active - lọc theo danh mục và trạng thái, kết hợp với keyword")
    void shouldFilterByCategoryAndActive() throws Exception {
        Long chickenId = saveCategory("Cám gà", true).getId();
        long deactivatedId = createdId(createProduct(productJson("Cám heo nái 25kg", bagId, "25")));
        mockMvc.perform(delete("/api/products/{id}", deactivatedId)).andExpect(status().isNoContent());
        createProduct(productJson("Cám heo con 25kg", bagId, "25"));
        createProduct(productJsonWithCategory("Cám gà đẻ 40kg", chickenId));

        mockMvc.perform(get("/api/products").param("categoryId", categoryId.toString()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2));

        mockMvc.perform(get("/api/products").param("categoryId", categoryId.toString()).param("active", "false"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Cám heo nái 25kg"));

        mockMvc.perform(get("/api/products").param("active", "true").param("keyword", "đẻ"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].categoryName").value("Cám gà"));
    }

    @Test
    @DisplayName("GET ?categoryId=abc - 400 VALIDATION_FAILED, không phải 500")
    void shouldRejectInvalidFilterType() throws Exception {
        mockMvc.perform(get("/api/products").param("categoryId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET sort theo thuộc tính không tồn tại - 400, không phải 500")
    void shouldRejectInvalidSort() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "khongCo,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("BR-STK-03: mức tồn tối thiểu - tùy chọn khi tạo, sửa được, PUT không gửi = bỏ mức")
    void shouldManageMinStockQuantity() throws Exception {
        createProduct(productJson("Cám vịt 25kg", bagId, "25"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.minStockQuantity").doesNotExist());

        Long id = createdId(createProduct("""
                {"name": "Cám heo con 25kg", "categoryId": %d, "unitId": %d, "netWeightKg": 25, "minStockQuantity": 50}
                """.formatted(categoryId, bagId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.minStockQuantity").value(50)));

        mockMvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Cám heo con 25kg", "categoryId": %d, "unitId": %d, "netWeightKg": 25,
                         "active": true, "minStockQuantity": 80}
                        """.formatted(categoryId, bagId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minStockQuantity").value(80));
        mockMvc.perform(put("/api/products/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Cám heo con 25kg", "categoryId": %d, "unitId": %d, "netWeightKg": 25, "active": true}
                        """.formatted(categoryId, bagId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minStockQuantity").doesNotExist());
    }

    @Test
    @DisplayName("BR-STK-03: tồn tối thiểu âm - 400; số lẻ - 400")
    void shouldValidateMinStockQuantity() throws Exception {
        createProduct("""
                {"name": "Cám heo", "categoryId": %d, "unitId": %d, "netWeightKg": 25, "minStockQuantity": -1}
                """.formatted(categoryId, bagId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("minStockQuantity"));
        createProduct("""
                {"name": "Cám heo", "categoryId": %d, "unitId": %d, "netWeightKg": 25, "minStockQuantity": 2.5}
                """.formatted(categoryId, bagId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("minStockQuantity"));
    }

    private Category saveCategory(String name, boolean active) {
        Category category = new Category();
        category.setName(name);
        category.setActive(active);
        return categoryRepository.save(category);
    }

    private ResultActions createProduct(String json) throws Exception {
        return mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ProductUnit saveUnit(String name, boolean active) {
        ProductUnit unit = new ProductUnit();
        unit.setName(name);
        unit.setActive(active);
        return unitRepository.save(unit);
    }

    private String productJson(String name, Long unitId, String netWeightKg) {
        return """
                {"name": "%s", "categoryId": %d, "unitId": %d, "netWeightKg": %s}
                """.formatted(name, categoryId, unitId, netWeightKg);
    }

    private String productJsonWithCategory(String name, Long catId) {
        return """
                {"name": "%s", "categoryId": %d, "unitId": %d, "netWeightKg": 25}
                """.formatted(name, catId, bagId);
    }
}
