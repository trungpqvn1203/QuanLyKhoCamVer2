package com.grainwarehouse.warehouse;

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

/** Test API kho trên DB thật; mỗi test rollback nhờ @Transactional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WarehouseApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("BR-WH-01, BR-WH-02: POST - 201, mã kho tự sinh KHO + số, active=true")
    void shouldCreateWarehouseWithGeneratedCode() throws Exception {
        createWarehouse(warehouseJson("Kho Hà Nội", "CENTRAL", "Số 1 Giải Phóng, Hà Nội"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value(matchesPattern("KHO\\d{3,}")))
                .andExpect(jsonPath("$.data.name").value("Kho Hà Nội"))
                .andExpect(jsonPath("$.data.type").value("CENTRAL"))
                .andExpect(jsonPath("$.data.address").value("Số 1 Giải Phóng, Hà Nội"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("BR-WH-01: POST thiếu tên, địa chỉ rỗng, thiếu loại - 400 VALIDATION_FAILED, details đủ field")
    void shouldRejectInvalidFields() throws Exception {
        createWarehouse("{\"address\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[*].field").value(hasItems("name", "type", "address")));
    }

    @Test
    @DisplayName("BR-WH-01: POST type=WHOLESALE - 400 VALIDATION_FAILED tại field type")
    void shouldRejectUnknownType() throws Exception {
        createWarehouse(warehouseJson("Kho Hà Nội", "WHOLESALE", "Hà Nội"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("type"));
    }

    @Test
    @DisplayName("BR-WH-03: POST tên trùng (khác hoa thường) - 409 DUPLICATE_RESOURCE")
    void shouldRejectDuplicateName() throws Exception {
        createWarehouse(warehouseJson("Kho Hà Nội", "CENTRAL", "Hà Nội")).andExpect(status().isCreated());

        createWarehouse(warehouseJson("kho hà nội", "RETAIL", "Hà Nội"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @DisplayName("BR-WH-04: tạo lại tên của kho đã ngừng hoạt động - 409, gợi ý mở lại kèm mã kho")
    void shouldRejectRecreatingDeactivatedWarehouse() throws Exception {
        ResultActions created = createWarehouse(warehouseJson("Kho Bắc Ninh", "RETAIL", "Bắc Ninh"));
        long id = createdId(created);
        String code = readCode(created);
        mockMvc.perform(delete("/api/warehouses/{id}", id)).andExpect(status().isNoContent());

        createWarehouse(warehouseJson("kho bắc ninh", "RETAIL", "Bắc Ninh"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.error.message").value(
                        "Kho 'Kho Bắc Ninh' (" + code + ") đã tồn tại nhưng đang ngừng hoạt động."
                                + " Hãy mở lại kho thay vì tạo mới"));
    }

    @Test
    @DisplayName("BR-WH-04: PUT active=true - mở lại kho đã ngừng hoạt động, giữ nguyên mã")
    void shouldReactivateWarehouse() throws Exception {
        ResultActions created = createWarehouse(warehouseJson("Kho Bắc Ninh", "RETAIL", "Bắc Ninh"));
        long id = createdId(created);
        String code = readCode(created);
        mockMvc.perform(delete("/api/warehouses/{id}", id)).andExpect(status().isNoContent());

        updateWarehouse(id, "Kho Bắc Ninh", "RETAIL", "Bắc Ninh", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.code").value(code));
    }

    @Test
    @DisplayName("BR-WH-03: PUT đổi tên trùng kho khác - 409; giữ nguyên tên của chính nó - 200")
    void shouldCheckDuplicateNameOnUpdate() throws Exception {
        createWarehouse(warehouseJson("Kho Hà Nội", "CENTRAL", "Hà Nội"));
        long id = createdId(createWarehouse(warehouseJson("Kho Hải Phòng", "RETAIL", "Hải Phòng")));

        updateWarehouse(id, "KHO HÀ NỘI", "RETAIL", "Hải Phòng", true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"));

        updateWarehouse(id, "Kho Hải Phòng", "CENTRAL", "Lê Chân, Hải Phòng", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("CENTRAL"))
                .andExpect(jsonPath("$.data.address").value("Lê Chân, Hải Phòng"));
    }

    @Test
    @DisplayName("BR-WH-02: PUT gửi kèm code - code bị bỏ qua, không đổi")
    void shouldNotChangeCodeOnUpdate() throws Exception {
        ResultActions created = createWarehouse(warehouseJson("Kho Hà Nội", "CENTRAL", "Hà Nội"));
        long id = createdId(created);
        String originalCode = readCode(created);

        mockMvc.perform(put("/api/warehouses/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "HACK01", "name": "Kho Hà Nội 2", "type": "CENTRAL",
                                 "address": "Hà Nội", "active": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Kho Hà Nội 2"))
                .andExpect(jsonPath("$.data.code").value(originalCode));
    }

    @Test
    @DisplayName("BR-WH-04: DELETE - 204, kho vẫn còn nhưng active=false")
    void shouldSoftDelete() throws Exception {
        long id = createdId(createWarehouse(warehouseJson("Kho Hà Nội", "CENTRAL", "Hà Nội")));

        mockMvc.perform(delete("/api/warehouses/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/warehouses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @DisplayName("GET kho không tồn tại - 404 RESOURCE_NOT_FOUND")
    void shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/warehouses/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET danh sách - cấu trúc data.items + data.pagination theo contract-spec")
    void shouldReturnPaginatedList() throws Exception {
        createWarehouse(warehouseJson("Kho Hà Nội", "CENTRAL", "Hà Nội"));
        createWarehouse(warehouseJson("Kho Hải Phòng", "RETAIL", "Hải Phòng"));

        mockMvc.perform(get("/api/warehouses").param("page", "0").param("size", "1"))
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
    @DisplayName("GET ?keyword - tìm theo mã hoặc tên, không phân biệt hoa thường; totalElements theo kết quả lọc")
    void shouldSearchByCodeOrName() throws Exception {
        String code = readCode(createWarehouse(warehouseJson("Kho Hà Nội", "CENTRAL", "Hà Nội")));
        createWarehouse(warehouseJson("Kho Hải Phòng", "CENTRAL", "Hải Phòng"));
        createWarehouse(warehouseJson("Cửa hàng Cầu Giấy", "RETAIL", "Hà Nội"));

        // Không tìm "kho": mã KHO001... của cả 3 bản ghi đều chứa "kho"
        mockMvc.perform(get("/api/warehouses").param("keyword", " KHO H ").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2));

        mockMvc.perform(get("/api/warehouses").param("keyword", code.toLowerCase()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value(code));

        // Gõ không dấu (migration V5)
        mockMvc.perform(get("/api/warehouses").param("keyword", "ha noi"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Kho Hà Nội"));
    }

    @Test
    @DisplayName("GET ?active - lọc theo trạng thái, kết hợp với keyword")
    void shouldFilterByActive() throws Exception {
        long deactivatedId = createdId(createWarehouse(warehouseJson("Kho Hà Nội", "CENTRAL", "Hà Nội")));
        mockMvc.perform(delete("/api/warehouses/{id}", deactivatedId)).andExpect(status().isNoContent());
        createWarehouse(warehouseJson("Kho Hải Phòng", "CENTRAL", "Hải Phòng"));

        mockMvc.perform(get("/api/warehouses").param("active", "false"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Kho Hà Nội"));

        mockMvc.perform(get("/api/warehouses").param("active", "true").param("keyword", "hà nội"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(0));
    }

    private ResultActions createWarehouse(String json) throws Exception {
        return mockMvc.perform(post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions updateWarehouse(long id, String name, String type, String address, boolean active)
            throws Exception {
        return mockMvc.perform(put("/api/warehouses/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "type": "%s", "address": "%s", "active": %s}
                        """.formatted(name, type, address, active)));
    }

    private String warehouseJson(String name, String type, String address) {
        return """
                {"name": "%s", "type": "%s", "address": "%s"}
                """.formatted(name, type, address);
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
