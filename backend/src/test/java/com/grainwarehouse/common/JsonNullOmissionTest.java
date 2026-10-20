package com.grainwarehouse.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract-spec: trường null bị BỎ khỏi JSON, không trả "field": null.
 * Kiểm tra trên chuỗi JSON thô vì jsonPath(...).doesNotExist() vẫn pass khi giá trị là null —
 * đã để lọt lỗi này từ đầu dự án: API trả "warehouseId": null, frontend so `!== undefined`
 * nên tưởng đang lọc theo kho và hiện tồn = 0.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JsonNullOmissionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Bản ghi có đủ loại trường tùy chọn để trống: mô tả SP, mức tồn tối thiểu, email/địa chỉ NCC
        Long categoryId = jdbcTemplate.queryForObject(
                "INSERT INTO category (name) VALUES ('Cám test') RETURNING id", Long.class);
        jdbcTemplate.update("INSERT INTO product (name, category_id, unit, net_weight_kg) VALUES ('Cám heo', ?, 'BAG', 25)",
                categoryId);
        jdbcTemplate.update("INSERT INTO supplier (name, contact_name) VALUES ('NCC test', 'A')");
        jdbcTemplate.update("INSERT INTO warehouse (name, type, address) VALUES ('Kho test', 'CENTRAL', 'Hà Nội')");
        jdbcTemplate.update("INSERT INTO customer (name, type, phone) VALUES ('Khách test', 'RETAIL', '0901234567')");
        jdbcTemplate.update("INSERT INTO employee (name, ton_rate) VALUES ('NV test', 50000)");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/products",
            "/api/inventory/products",
            "/api/suppliers",
            "/api/warehouses",
            "/api/categories",
            "/api/inbound-receipts",
            "/api/customers",
            "/api/employees"
    })
    @DisplayName("Danh sách không chứa \"field\": null")
    void listShouldOmitNullFields(String url) throws Exception {
        String body = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContainPattern("\"\\w+\"\\s*:\\s*null");
    }
}
