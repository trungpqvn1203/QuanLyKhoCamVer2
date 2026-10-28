package com.grainwarehouse.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** BR-PRT-01: thông tin cửa hàng in trên phiếu. Mỗi test rollback nhờ @Transactional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StoreInfoApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // DB test dùng chung: bắt đầu từ trạng thái "chưa cấu hình" (rollback sau test)
        jdbcTemplate.update("DELETE FROM store_info");
    }

    @Test
    @DisplayName("GET khi chưa cấu hình - 200, configured=false, không có trường nào khác")
    void shouldReportNotConfigured() throws Exception {
        mockMvc.perform(get("/api/store-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.name").doesNotExist());
    }

    @Test
    @DisplayName("PUT lần đầu tạo, lần sau ghi đè; cắt khoảng trắng, ô trống → bỏ khỏi JSON")
    void shouldCreateThenOverwrite() throws Exception {
        save("""
                {"name": "  Cửa hàng cám Minh Phát ", "address": "12 Lý Bôn, Thái Bình",
                 "phone": "0912 345 678", "receiptFooter": "Cảm ơn quý khách!"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã lưu thông tin cửa hàng"))
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.name").value("Cửa hàng cám Minh Phát"))
                .andExpect(jsonPath("$.data.updatedAt").exists());

        save("""
                {"name": "Cám Minh Phát", "address": "   ", "phone": "0912 345 678"}
                """)
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/store-info"))
                .andExpect(jsonPath("$.data.name").value("Cám Minh Phát"))
                .andExpect(jsonPath("$.data.address").doesNotExist())
                .andExpect(jsonPath("$.data.receiptFooter").doesNotExist());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM store_info", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("PUT thiếu tên - 400 VALIDATION_FAILED tại field name")
    void shouldRequireName() throws Exception {
        save("{\"name\": \"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
    }

    @Test
    @DisplayName("DB chặn dòng thứ hai (CHECK id = 1)")
    void dbShouldAllowSingleRowOnly() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO store_info (id, name) VALUES (2, 'Chi nhánh')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_store_info_single_row");
    }

    private ResultActions save(String json) throws Exception {
        return mockMvc.perform(put("/api/store-info").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
