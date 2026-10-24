package com.grainwarehouse.supplier;

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

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test API nhà cung cấp trên DB thật; mỗi test rollback nhờ @Transactional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SupplierApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("BR-SUP-01, BR-SUP-02: POST đủ trường - 201, mã NCC tự sinh, active=true")
    void shouldCreateSupplierWithGeneratedCode() throws Exception {
        createSupplier("""
                {"name": "Công ty CP Cám Việt", "taxCode": "0101 234 567-001",
                 "phone": "0901234567", "email": "a@camviet.vn", "address": "KCN Đồng Văn, Hà Nam"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value(matchesPattern("NCC\\d{3,}")))
                .andExpect(jsonPath("$.data.name").value("Công ty CP Cám Việt"))
                .andExpect(jsonPath("$.data.taxCode").value("0101234567-001"))
                .andExpect(jsonPath("$.data.phone").value("0901234567"))
                .andExpect(jsonPath("$.data.email").value("a@camviet.vn"))
                .andExpect(jsonPath("$.data.address").value("KCN Đồng Văn, Hà Nam"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("BR-SUP-01: chỉ có tên - 201; trường tùy chọn rỗng (kể cả MST) bị bỏ khỏi JSON")
    void shouldCreateWithOnlyRequiredFields() throws Exception {
        createSupplier("""
                {"name": "Nhà máy Cám Nam", "taxCode": " ", "phone": "  ", "email": ""}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.taxCode").doesNotExist())
                .andExpect(jsonPath("$.data.phone").doesNotExist())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.address").doesNotExist());
    }

    @Test
    @DisplayName("BR-SUP-01: thiếu tên, email sai - 400 VALIDATION_FAILED đủ field")
    void shouldRejectInvalidFields() throws Exception {
        createSupplier("""
                {"name": "   ", "email": "khong-phai-email"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[*].field").value(hasItems("name", "email")));
    }

    @Test
    @DisplayName("BR-SUP-09: MST sai định dạng (9 số, có chữ, chi nhánh 2 số) - 400 tại field taxCode")
    void shouldRejectMalformedTaxCode() throws Exception {
        for (String taxCode : new String[] {"010123456", "01012345AB", "0101234567-01"}) {
            createSupplier("""
                    {"name": "Công ty %s", "taxCode": "%s"}
                    """.formatted(taxCode, taxCode))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.details[0].field").value("taxCode"));
        }
    }

    @Test
    @DisplayName("BR-SUP-09: MST trùng NCC khác (khác cách gõ, kể cả NCC đã ngừng) - 409 nêu NCC đang giữ")
    void shouldRejectDuplicateTaxCode() throws Exception {
        ResultActions created = createSupplier("""
                {"name": "Công ty An Phú", "taxCode": "0301234567"}
                """);
        long id = createdId(created);
        String code = readCode(created);
        mockMvc.perform(delete("/api/suppliers/{id}", id)).andExpect(status().isNoContent());

        createSupplier("""
                {"name": "Công ty Bình Minh", "taxCode": "0301 234 567"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.error.message").value(
                        "Mã số thuế 0301234567 đã thuộc về nhà cung cấp 'Công ty An Phú' (" + code + "),"
                                + " nhà cung cấp này đang ngừng hoạt động. Hãy mở lại thay vì tạo mới"));
    }

    @Test
    @DisplayName("BR-SUP-09: nhiều NCC không có MST - hợp lệ; PUT giữ nguyên MST của chính mình - không báo trùng")
    void shouldAllowManyWithoutTaxCodeAndKeepOwnTaxCode() throws Exception {
        createSupplier(supplierJson("Nhà máy A")).andExpect(status().isCreated());
        createSupplier(supplierJson("Nhà máy B")).andExpect(status().isCreated());

        long id = createdId(createSupplier("""
                {"name": "Nhà máy C", "taxCode": "0401234567"}
                """));
        updateSupplier(id, """
                {"name": "Nhà máy C mới", "taxCode": "0401234567", "active": true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taxCode").value("0401234567"));
    }

    @Test
    @DisplayName("BR-SUP-09: ô tìm kiếm khớp theo MST")
    void shouldSearchByTaxCode() throws Exception {
        createSupplier("""
                {"name": "Công ty An Phú", "taxCode": "0301234567"}
                """);
        createSupplier(supplierJson("Công ty Bình Minh"));

        mockMvc.perform(get("/api/suppliers").param("keyword", "030123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Công ty An Phú"));
    }

    @Test
    @DisplayName("BR-SUP-03: POST tên trùng (khác hoa thường) - 409 DUPLICATE_RESOURCE")
    void shouldRejectDuplicateName() throws Exception {
        createSupplier(supplierJson("Công ty CP Cám Việt")).andExpect(status().isCreated());

        createSupplier(supplierJson("CÔNG TY CP CÁM VIỆT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("BR-SUP-04: tạo lại tên của NCC đã ngừng hoạt động - 409, gợi ý mở lại kèm mã NCC")
    void shouldRejectRecreatingDeactivatedSupplier() throws Exception {
        ResultActions created = createSupplier(supplierJson("Nhà máy Cám Nam"));
        long id = createdId(created);
        String code = readCode(created);
        mockMvc.perform(delete("/api/suppliers/{id}", id)).andExpect(status().isNoContent());

        createSupplier(supplierJson("nhà máy cám nam"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.error.message").value(
                        "Nhà cung cấp 'Nhà máy Cám Nam' (" + code + ") đã tồn tại nhưng đang ngừng hoạt động."
                                + " Hãy mở lại nhà cung cấp thay vì tạo mới"));
    }

    @Test
    @DisplayName("BR-SUP-04: PUT active=true - mở lại NCC đã ngừng hoạt động, giữ nguyên mã")
    void shouldReactivateSupplier() throws Exception {
        ResultActions created = createSupplier(supplierJson("Nhà máy Cám Nam"));
        long id = createdId(created);
        String code = readCode(created);
        mockMvc.perform(delete("/api/suppliers/{id}", id)).andExpect(status().isNoContent());

        updateSupplier(id, """
                {"name": "Nhà máy Cám Nam", "active": true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.code").value(code));
    }

    @Test
    @DisplayName("BR-SUP-02, BR-SUP-03: PUT gửi kèm code bị bỏ qua; đổi tên trùng NCC khác - 409")
    void shouldUpdateWithoutChangingCode() throws Exception {
        createSupplier(supplierJson("Công ty CP Cám Việt"));
        ResultActions created = createSupplier(supplierJson("Nhà máy Cám Nam"));
        long id = createdId(created);
        String originalCode = readCode(created);

        updateSupplier(id, """
                {"name": "công ty cp cám việt", "active": true}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));

        updateSupplier(id, """
                {"code": "HACK01", "name": "Nhà máy Cám Nam 2", "taxCode": "0501234567",
                 "phone": "0283456789", "active": true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(originalCode))
                .andExpect(jsonPath("$.data.name").value("Nhà máy Cám Nam 2"))
                .andExpect(jsonPath("$.data.taxCode").value("0501234567"))
                .andExpect(jsonPath("$.data.phone").value("0283456789"));
    }

    @Test
    @DisplayName("BR-SUP-04: DELETE - 204, NCC vẫn còn nhưng active=false")
    void shouldSoftDelete() throws Exception {
        long id = createdId(createSupplier(supplierJson("Nhà máy Cám Nam")));

        mockMvc.perform(delete("/api/suppliers/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/suppliers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("GET NCC không tồn tại - 404 RESOURCE_NOT_FOUND")
    void shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/suppliers/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET danh sách - cấu trúc data.items + data.pagination theo contract-spec")
    void shouldReturnPaginatedList() throws Exception {
        createSupplier(supplierJson("Công ty CP Cám Việt"));
        createSupplier(supplierJson("Nhà máy Cám Nam"));

        mockMvc.perform(get("/api/suppliers").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2))
                .andExpect(jsonPath("$.data.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.data.pagination.isFirst").value(true))
                .andExpect(jsonPath("$.data.pagination.isLast").value(false));
    }

    @Test
    @DisplayName("GET ?keyword - tìm theo mã hoặc tên, không phân biệt hoa thường; totalElements theo kết quả lọc")
    void shouldSearchByCodeOrName() throws Exception {
        String code = readCode(createSupplier(supplierJson("Công ty CP Cám Việt")));
        createSupplier(supplierJson("Công ty Cám Bắc"));
        createSupplier(supplierJson("Nhà máy Thức ăn Nam"));

        mockMvc.perform(get("/api/suppliers").param("keyword", "  CÔNG TY  ").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2));

        mockMvc.perform(get("/api/suppliers").param("keyword", code.toLowerCase()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value(code));

        // Gõ không dấu (migration V5)
        mockMvc.perform(get("/api/suppliers").param("keyword", "cam viet"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Công ty CP Cám Việt"));
    }

    @Test
    @DisplayName("GET ?keyword=% - ký tự đại diện LIKE được hiểu là chữ thường, không khớp tất cả")
    void shouldEscapeLikeWildcards() throws Exception {
        createSupplier(supplierJson("Công ty CP Cám Việt"));

        mockMvc.perform(get("/api/suppliers").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pagination.totalElements").value(0));
    }

    @Test
    @DisplayName("GET ?active - lọc theo trạng thái, kết hợp với keyword")
    void shouldFilterByActive() throws Exception {
        long deactivatedId = createdId(createSupplier(supplierJson("Nhà máy Cám Nam")));
        mockMvc.perform(delete("/api/suppliers/{id}", deactivatedId)).andExpect(status().isNoContent());
        createSupplier(supplierJson("Nhà máy Cám Bắc"));
        createSupplier(supplierJson("Công ty CP Cám Việt"));

        mockMvc.perform(get("/api/suppliers").param("active", "false"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Nhà máy Cám Nam"));

        mockMvc.perform(get("/api/suppliers").param("active", "true").param("keyword", "nhà máy"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Nhà máy Cám Bắc"));
    }

    @Test
    @DisplayName("GET ?active=abc - 400 VALIDATION_FAILED, không phải 500")
    void shouldRejectInvalidActiveFilter() throws Exception {
        mockMvc.perform(get("/api/suppliers").param("active", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private ResultActions createSupplier(String json) throws Exception {
        return mockMvc.perform(post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions updateSupplier(long id, String json) throws Exception {
        return mockMvc.perform(put("/api/suppliers/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String supplierJson(String name) {
        return """
                {"name": "%s"}
                """.formatted(name);
    }

    private static long createdId(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.<Number>read(body, "$.data.id").longValue();
    }

    private static String readCode(ResultActions result) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                result.andReturn().getResponse().getContentAsString(), "$.data.code");
    }
}
