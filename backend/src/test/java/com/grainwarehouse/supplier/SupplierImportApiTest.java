package com.grainwarehouse.supplier;

import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import com.grainwarehouse.support.ExcelTestFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static com.grainwarehouse.support.ExcelTestFiles.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhập nhà cung cấp từ Excel (BR-XLS). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SupplierImportApiTest {

    private static final String BASE = "/api/suppliers/import";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SupplierRepository supplierRepository;

    @Test
    @DisplayName("commit - 201; ô tùy chọn để trống lưu NULL")
    void shouldImportSuppliers() throws Exception {
        mockMvc.perform(multipart(BASE).file(upload(file(
                        row("Công ty An Phú", "Chị Lan", "0283 123 4567", "lan@anphu.vn", "KCN Tân Tạo"),
                        row("Công ty Bình Minh", "Anh Tú")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.importedCount").value(2));

        Supplier minimal = supplierRepository.findByNameIgnoreCase("Công ty Bình Minh").orElseThrow();
        assertThat(minimal.getCode()).matches("NCC\\d{3,}");
        assertThat(minimal.getEmail()).isNull();
    }

    @Test
    @DisplayName("BR-SUP-03, BR-SUP-04: tên trùng NCC đã ngừng - lỗi kèm hướng dẫn mở lại; email sai - lỗi cột Email")
    void shouldReportDuplicateAndInvalidEmail() throws Exception {
        Supplier inactive = new Supplier();
        inactive.setName("Công ty Cũ");
        inactive.setContactName("Ông Ba");
        inactive.setActive(false);
        supplierRepository.save(inactive);

        mockMvc.perform(multipart(BASE + "/preview").file(upload(file(
                        row("công ty cũ", "Ông Ba"),
                        row("Công ty Mới", "Bà Tư", null, "khong-phai-email")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validRows").value(0))
                .andExpect(jsonPath("$.data.errors[0].field").value("name"))
                .andExpect(jsonPath("$.data.errors[0].message").value(containsString("Hãy mở lại nhà cung cấp")))
                .andExpect(jsonPath("$.data.errors[1].field").value("email"))
                .andExpect(jsonPath("$.data.errors[1].column").value("Email"));
    }

    @SafeVarargs
    private byte[] file(List<Object>... rows) throws Exception {
        return ExcelTestFiles.fill(ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/template"), Arrays.asList(rows));
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }
}
