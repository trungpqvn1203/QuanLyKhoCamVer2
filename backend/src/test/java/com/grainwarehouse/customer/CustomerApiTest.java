package com.grainwarehouse.customer;

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

/** Test API khách hàng trên DB thật; mỗi test rollback nhờ @Transactional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("BR-CUS-01, BR-CUS-02: POST đủ trường - 201, mã KH tự sinh, active=true")
    void shouldCreateCustomerWithGeneratedCode() throws Exception {
        createCustomer("""
                {"name": "Đại lý Minh Phát", "type": "DEALER", "phone": "0901234567",
                 "contactName": "Nguyễn Văn Minh", "address": "Chợ Đồng Văn, Hà Nam", "note": "Giao sáng"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(matchesPattern("KH\\d{4,}")))
                .andExpect(jsonPath("$.data.name").value("Đại lý Minh Phát"))
                .andExpect(jsonPath("$.data.type").value("DEALER"))
                .andExpect(jsonPath("$.data.phone").value("0901234567"))
                .andExpect(jsonPath("$.data.contactName").value("Nguyễn Văn Minh"))
                .andExpect(jsonPath("$.data.address").value("Chợ Đồng Văn, Hà Nam"))
                .andExpect(jsonPath("$.data.note").value("Giao sáng"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("BR-CUS-01: chỉ trường bắt buộc - 201; trường tùy chọn rỗng bị bỏ khỏi JSON")
    void shouldCreateWithOnlyRequiredFields() throws Exception {
        createCustomer("""
                {"name": "Anh Tư", "type": "RETAIL", "phone": "0912345678", "contactName": "  ", "note": ""}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contactName").doesNotExist())
                .andExpect(jsonPath("$.data.address").doesNotExist())
                .andExpect(jsonPath("$.data.note").doesNotExist());
    }

    @Test
    @DisplayName("BR-CUS-01: thiếu tên, loại, SĐT - 400 VALIDATION_FAILED đủ field")
    void shouldRejectMissingRequiredFields() throws Exception {
        createCustomer("""
                {"name": "  ", "phone": ""}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[*].field").value(hasItems("name", "type", "phone")));
    }

    @Test
    @DisplayName("BR-CUS-03: loại khách ngoài danh sách - 400 VALIDATION_FAILED, không phải 500")
    void shouldRejectUnknownType() throws Exception {
        createCustomer(customerJson("Đại lý A", "WHOLESALE", "0901234567"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("BR-CUS-06: SĐT có khoảng trắng/chấm/gạch được lưu ở dạng chuẩn hóa")
    void shouldStoreNormalizedPhone() throws Exception {
        createCustomer(customerJson("Đại lý A", "DEALER", " 0901.234-567 "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phone").value("0901234567"));
    }

    @Test
    @DisplayName("BR-CUS-06: SĐT sai định dạng - 400 VALIDATION_FAILED gắn vào field phone")
    void shouldRejectInvalidPhone() throws Exception {
        createCustomer(customerJson("Đại lý A", "DEALER", "+84901234567"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("phone"));
    }

    @Test
    @DisplayName("BR-CUS-04: tên trùng được phép; SĐT trùng (gõ khác cách) - 409 nêu khách đang giữ số")
    void shouldRejectDuplicatePhoneButAllowDuplicateName() throws Exception {
        String code = readCode(createCustomer(customerJson("Trại heo Minh", "FARM", "0901234567")));
        createCustomer(customerJson("Trại heo Minh", "FARM", "0912345678")).andExpect(status().isCreated());

        createCustomer(customerJson("Khách khác", "RETAIL", "0901 234 567"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.error.message").value(
                        "Số điện thoại 0901234567 đã thuộc về khách hàng 'Trại heo Minh' (" + code + ")"));
    }

    @Test
    @DisplayName("BR-CUS-05: tạo khách mới bằng SĐT của khách đã ngừng - 409, gợi ý mở lại")
    void shouldRejectRecreatingDeactivatedCustomer() throws Exception {
        ResultActions created = createCustomer(customerJson("Đại lý A", "DEALER", "0901234567"));
        long id = createdId(created);
        String code = readCode(created);
        mockMvc.perform(delete("/api/customers/{id}", id)).andExpect(status().isNoContent());

        createCustomer(customerJson("Đại lý A mới", "DEALER", "0901234567"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(
                        "Số điện thoại 0901234567 đã thuộc về khách hàng 'Đại lý A' (" + code + "),"
                                + " khách này đang ngừng hoạt động. Hãy mở lại khách hàng thay vì tạo mới"));
    }

    @Test
    @DisplayName("BR-CUS-05: DELETE - 204, khách vẫn còn nhưng active=false; PUT active=true mở lại, giữ mã")
    void shouldSoftDeleteAndReactivate() throws Exception {
        ResultActions created = createCustomer(customerJson("Đại lý A", "DEALER", "0901234567"));
        long id = createdId(created);
        String code = readCode(created);

        mockMvc.perform(delete("/api/customers/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        updateCustomer(id, """
                {"name": "Đại lý A", "type": "DEALER", "phone": "0901234567", "active": true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.code").value(code));
    }

    @Test
    @DisplayName("BR-CUS-02, BR-CUS-04: PUT gửi kèm code bị bỏ qua; giữ SĐT của chính mình được; lấy SĐT khách khác - 409")
    void shouldUpdateWithoutChangingCode() throws Exception {
        createCustomer(customerJson("Đại lý A", "DEALER", "0901234567"));
        ResultActions created = createCustomer(customerJson("Trại B", "FARM", "0912345678"));
        long id = createdId(created);
        String originalCode = readCode(created);

        updateCustomer(id, """
                {"name": "Trại B", "type": "FARM", "phone": "0901.234.567", "active": true}
                """)
                .andExpect(status().isConflict());

        updateCustomer(id, """
                {"code": "HACK01", "name": "Trại B mở rộng", "type": "DEALER", "phone": "0912 345 678",
                 "address": "Phủ Lý", "active": true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(originalCode))
                .andExpect(jsonPath("$.data.name").value("Trại B mở rộng"))
                .andExpect(jsonPath("$.data.type").value("DEALER"))
                .andExpect(jsonPath("$.data.phone").value("0912345678"))
                .andExpect(jsonPath("$.data.address").value("Phủ Lý"));
    }

    @Test
    @DisplayName("GET khách không tồn tại - 404 RESOURCE_NOT_FOUND")
    void shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/customers/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET ?keyword - tìm theo mã, tên (không dấu) hoặc SĐT; totalElements theo kết quả lọc")
    void shouldSearchByCodeNameOrPhone() throws Exception {
        String code = readCode(createCustomer(customerJson("Đại lý Minh Phát", "DEALER", "0901234567")));
        createCustomer(customerJson("Trại heo Đồng Văn", "FARM", "0912345678"));
        createCustomer(customerJson("Anh Tư", "RETAIL", "0987654321"));

        mockMvc.perform(get("/api/customers").param("keyword", "dong van"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Trại heo Đồng Văn"));

        mockMvc.perform(get("/api/customers").param("keyword", "0987"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Anh Tư"));

        mockMvc.perform(get("/api/customers").param("keyword", code.toLowerCase()))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value(code));
    }

    @Test
    @DisplayName("GET ?type&active - lọc theo loại khách và trạng thái, kết hợp được")
    void shouldFilterByTypeAndActive() throws Exception {
        long deactivatedId = createdId(createCustomer(customerJson("Trại A", "FARM", "0901234567")));
        mockMvc.perform(delete("/api/customers/{id}", deactivatedId)).andExpect(status().isNoContent());
        createCustomer(customerJson("Trại B", "FARM", "0912345678"));
        createCustomer(customerJson("Đại lý C", "DEALER", "0987654321"));

        mockMvc.perform(get("/api/customers").param("type", "FARM"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(2));

        mockMvc.perform(get("/api/customers").param("type", "FARM").param("active", "true"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Trại B"));
    }

    @Test
    @DisplayName("GET ?type=abc - 400 VALIDATION_FAILED, không phải 500")
    void shouldRejectInvalidTypeFilter() throws Exception {
        mockMvc.perform(get("/api/customers").param("type", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private ResultActions createCustomer(String json) throws Exception {
        return mockMvc.perform(post("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions updateCustomer(long id, String json) throws Exception {
        return mockMvc.perform(put("/api/customers/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String customerJson(String name, String type, String phone) {
        return """
                {"name": "%s", "type": "%s", "phone": "%s"}
                """.formatted(name, type, phone);
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
