package com.grainwarehouse.product;

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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test API danh mục trên DB thật; mỗi test rollback nhờ @Transactional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CategoryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/categories - 201, trả envelope thành công")
    void shouldCreateCategory() throws Exception {
        createCategory("{\"name\": \"  Cám gà  \", \"description\": \"Cám cho gà\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tạo danh mục thành công"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("Cám gà"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("BR-CAT-02: POST tên trùng (khác hoa thường) - 409 DUPLICATE_RESOURCE")
    void shouldRejectDuplicateName() throws Exception {
        createCategory("{\"name\": \"Cám gà\"}").andExpect(status().isCreated());

        createCategory("{\"name\": \"CÁM GÀ\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("BR-CAT-04: tạo lại tên của danh mục đã ngừng dùng - 409, gợi ý mở lại")
    void shouldRejectRecreatingDeactivatedCategory() throws Exception {
        long id = createdId(createCategory("{\"name\": \"Cám lợn\"}"));
        mockMvc.perform(delete("/api/categories/{id}", id)).andExpect(status().isNoContent());

        createCategory("{\"name\": \"CÁM LỢN\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.error.message").value(
                        "Danh mục 'Cám lợn' đã tồn tại nhưng đang ngừng dùng. Hãy mở lại thay vì tạo mới"));
    }

    @Test
    @DisplayName("POST tên rỗng - 400 VALIDATION_FAILED, details chỉ ra field name")
    void shouldRejectBlankName() throws Exception {
        createCategory("{\"name\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[*].field").value(hasItem("name")))
                .andExpect(jsonPath("$.error.details[0].rejectedValue").doesNotExist());
    }

    @Test
    @DisplayName("BR-CAT-03: DELETE - 204, danh mục chuyển active=false, PUT mở lại được")
    void shouldSoftDeleteAndReactivate() throws Exception {
        long id = createdId(createCategory("{\"name\": \"Cám cá\"}"));

        mockMvc.perform(delete("/api/categories/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/categories/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(put("/api/categories/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Cám cá\", \"active\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("GET id không tồn tại - 404 RESOURCE_NOT_FOUND")
    void shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/categories/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private ResultActions createCategory(String json) throws Exception {
        return mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    static long createdId(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.<Number>read(body, "$.data.id").longValue();
    }
}
