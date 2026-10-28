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

import static com.grainwarehouse.product.CategoryApiTest.createdId;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Test API đơn vị tính (BR-UNIT-01..04) trên DB thật; mỗi test rollback nhờ @Transactional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductUnitApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET - có sẵn Bao và Kg từ V23; chỉ Kg có fixedNetWeightKg")
    void shouldListSeededUnits() throws Exception {
        mockMvc.perform(get("/api/product-units").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].name").value(hasItems("Bao", "Kg")))
                .andExpect(jsonPath("$.data.items[?(@.name == 'Kg')].fixedNetWeightKg").value(hasItem(1.0)))
                .andExpect(jsonPath("$.data.items[?(@.name == 'Bao')].fixedNetWeightKg").isEmpty());
    }

    @Test
    @DisplayName("POST - 201, tên được cắt khoảng trắng; gửi kèm fixedNetWeightKg bị bỏ qua (BR-UNIT-03)")
    void shouldCreateUnitIgnoringFixedWeight() throws Exception {
        createUnit("{\"name\": \"  Túi  \", \"fixedNetWeightKg\": 5}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Tạo đơn vị thành công"))
                .andExpect(jsonPath("$.data.name").value("Túi"))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.fixedNetWeightKg").doesNotExist());
    }

    @Test
    @DisplayName("BR-UNIT-01: POST tên trùng (khác hoa thường) với đơn vị có sẵn - 409 DUPLICATE_RESOURCE")
    void shouldRejectDuplicateName() throws Exception {
        createUnit("{\"name\": \"BAO\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.error.message").value("Đơn vị 'Bao' đã tồn tại"));
    }

    @Test
    @DisplayName("BR-UNIT-02: DELETE = ngừng dùng; tạo lại cùng tên - 409 gợi ý mở lại; PUT active=true mở lại")
    void shouldDeactivateAndReopen() throws Exception {
        long id = createdId(createUnit("{\"name\": \"Thùng\"}"));
        mockMvc.perform(delete("/api/product-units/{id}", id)).andExpect(status().isNoContent());

        createUnit("{\"name\": \"thùng\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(
                        "Đơn vị 'Thùng' đã tồn tại nhưng đang ngừng dùng. Hãy mở lại thay vì tạo mới"));

        mockMvc.perform(put("/api/product-units/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Thùng carton\", \"active\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Thùng carton"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("POST tên rỗng / quá 50 ký tự - 400 VALIDATION_FAILED tại field name")
    void shouldValidateName() throws Exception {
        createUnit("{\"name\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
        createUnit("{\"name\": \"" + "x".repeat(51) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
    }

    @Test
    @DisplayName("PUT / GET id không tồn tại - 404")
    void shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/product-units/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    private ResultActions createUnit(String json) throws Exception {
        return mockMvc.perform(post("/api/product-units").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
