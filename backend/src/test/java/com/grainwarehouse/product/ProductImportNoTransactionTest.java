package com.grainwarehouse.product;

import com.grainwarehouse.support.ExcelTestFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;

import static com.grainwarehouse.support.ExcelTestFiles.upload;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cố ý KHÔNG @Transactional: chạy giống request thật, không có transaction bao ngoài. Lớp test có @Transactional
 * che mất lỗi "ProductSupplierLinkService là MANDATORY nhưng sinh mẫu / đọc file gọi khi chưa có transaction".
 * Chỉ tải mẫu và preview (không ghi DB) → không cần dọn dữ liệu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductImportNoTransactionTest {

    private static final String BASE = "/api/products/import";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Tải mẫu (dropdown NCC) và preview có cột NCC khi không có transaction bao ngoài - 200, không phải 500")
    void shouldReadSuppliersWithoutOuterTransaction() throws Exception {
        byte[] template = ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/template");
        List<Object> row = Arrays.asList("Cám không transaction", "Danh mục không có", "Bao", 25, null, "NCC99999");

        mockMvc.perform(multipart(BASE + "/preview").file(upload(ExcelTestFiles.fill(template, List.of(row)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errors[?(@.field == 'supplier1')].message")
                        .value(hasItem("Nhà cung cấp mã 'NCC99999' không tồn tại")));
    }
}
