package com.grainwarehouse.employee;

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

/** Test API nhân viên trên DB thật; mỗi test rollback nhờ @Transactional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("BR-EMP-01, BR-EMP-02: POST hưởng cả hai cách - 201, mã NV tự sinh, SĐT chuẩn hóa")
    void shouldCreateEmployeeWithBothRates() throws Exception {
        createEmployee("""
                {"name": "Nguyễn Văn Bốc", "phone": "0901 234 567", "dailyWage": 300000, "unloadTonRate": 52500.50}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(matchesPattern("NV\\d{3,}")))
                .andExpect(jsonPath("$.data.name").value("Nguyễn Văn Bốc"))
                .andExpect(jsonPath("$.data.phone").value("0901234567"))
                .andExpect(jsonPath("$.data.dailyWage").value(300000))
                .andExpect(jsonPath("$.data.unloadTonRate").value(52500.50))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("BR-EMP-02: chỉ một cách tính lương - 201, cách còn lại và SĐT bị bỏ khỏi JSON")
    void shouldCreateWithOnlyOneRate() throws Exception {
        createEmployee("""
                {"name": "Trần Thủ Kho", "phone": " ", "dailyWage": 350000}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unloadTonRate").doesNotExist())
                .andExpect(jsonPath("$.data.phone").doesNotExist());
    }

    @Test
    @DisplayName("BR-EMP-02: không có đơn giá nào - 400 field dailyWage")
    void shouldRejectWithoutAnyRate() throws Exception {
        createEmployee("""
                {"name": "Lê C"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("dailyWage"));
    }

    @Test
    @DisplayName("BR-EMP-02, BR-MON-04: đơn giá âm, quá 2 chữ số lẻ, thiếu tên - 400 đủ field")
    void shouldRejectInvalidFields() throws Exception {
        createEmployee("""
                {"name": " ", "dailyWage": -1, "unloadTonRate": 1000.123}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[*].field").value(hasItems("name", "dailyWage", "unloadTonRate")));
    }

    @Test
    @DisplayName("BR-EMP-04: chỉ có đơn giá tấn giao hàng - 201; có cả hai đơn giá tấn - lưu riêng từng loại")
    void shouldStoreUnloadAndDeliveryRatesSeparately() throws Exception {
        createEmployee("""
                {"name": "Lê Giao Hàng", "deliveryTonRate": 60000}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deliveryTonRate").value(60000))
                .andExpect(jsonPath("$.data.unloadTonRate").doesNotExist())
                .andExpect(jsonPath("$.data.dailyWage").doesNotExist());

        createEmployee("""
                {"name": "Nguyễn Văn Bốc", "unloadTonRate": 52500, "deliveryTonRate": 60000.50}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.unloadTonRate").value(52500))
                .andExpect(jsonPath("$.data.deliveryTonRate").value(60000.50));
    }

    @Test
    @DisplayName("BR-EMP-04: đơn giá tấn giao hàng âm hoặc quá 2 chữ số lẻ - 400 field deliveryTonRate")
    void shouldRejectInvalidDeliveryRate() throws Exception {
        createEmployee("""
                {"name": "Lê C", "dailyWage": 300000, "deliveryTonRate": -1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("deliveryTonRate"));
        createEmployee("""
                {"name": "Lê C", "dailyWage": 300000, "deliveryTonRate": 1000.123}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("deliveryTonRate"));
    }

    @Test
    @DisplayName("BR-EMP-03: SĐT sai định dạng - 400 field phone")
    void shouldRejectInvalidPhone() throws Exception {
        createEmployee("""
                {"name": "Lê C", "phone": "12345", "unloadTonRate": 50000}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("phone"));
    }

    @Test
    @DisplayName("BR-EMP-03: tên và SĐT được trùng giữa hai nhân viên")
    void shouldAllowDuplicateNameAndPhone() throws Exception {
        String json = """
                {"name": "Nguyễn Văn A", "phone": "0901234567", "unloadTonRate": 50000}
                """;
        createEmployee(json).andExpect(status().isCreated());
        createEmployee(json).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT bỏ đơn giá tấn, đổi lương ngày; gửi kèm code bị bỏ qua; bỏ cả hai - 400")
    void shouldUpdateRates() throws Exception {
        ResultActions created = createEmployee("""
                {"name": "Nguyễn Văn A", "dailyWage": 300000, "unloadTonRate": 50000}
                """);
        long id = createdId(created);
        String code = readCode(created);

        updateEmployee(id, """
                {"code": "HACK", "name": "Nguyễn Văn A", "dailyWage": 320000, "active": true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.dailyWage").value(320000))
                .andExpect(jsonPath("$.data.unloadTonRate").doesNotExist());

        updateEmployee(id, """
                {"name": "Nguyễn Văn A", "active": true}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("dailyWage"));
    }

    @Test
    @DisplayName("BR-EMP-01: DELETE - 204, nhân viên còn nhưng active=false; PUT active=true mở lại")
    void shouldSoftDeleteAndReactivate() throws Exception {
        long id = createdId(createEmployee("""
                {"name": "Nguyễn Văn A", "unloadTonRate": 50000}
                """));

        mockMvc.perform(delete("/api/employees/{id}", id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/employees/{id}", id)).andExpect(jsonPath("$.data.active").value(false));

        updateEmployee(id, """
                {"name": "Nguyễn Văn A", "unloadTonRate": 50000, "active": true}
                """)
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("GET ?keyword&active - tìm theo tên không dấu hoặc SĐT, lọc trạng thái")
    void shouldSearchAndFilter() throws Exception {
        createEmployee("""
                {"name": "Nguyễn Văn Bốc", "phone": "0901234567", "unloadTonRate": 50000}
                """);
        long inactiveId = createdId(createEmployee("""
                {"name": "Trần Thủ Kho", "phone": "0912345678", "dailyWage": 300000}
                """));
        mockMvc.perform(delete("/api/employees/{id}", inactiveId));

        mockMvc.perform(get("/api/employees").param("keyword", "boc"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Nguyễn Văn Bốc"));

        mockMvc.perform(get("/api/employees").param("keyword", "0912"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1));

        mockMvc.perform(get("/api/employees").param("active", "true"))
                .andExpect(jsonPath("$.data.pagination.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Nguyễn Văn Bốc"));
    }

    @Test
    @DisplayName("GET nhân viên không tồn tại - 404")
    void shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/employees/{id}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private ResultActions createEmployee(String json) throws Exception {
        return mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions updateEmployee(long id, String json) throws Exception {
        return mockMvc.perform(put("/api/employees/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json));
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
