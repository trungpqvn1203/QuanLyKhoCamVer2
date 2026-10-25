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
    @DisplayName("commit - 201; MST chuẩn hóa, giữ số 0 đầu; ô tùy chọn để trống lưu NULL")
    void shouldImportSuppliers() throws Exception {
        mockMvc.perform(multipart(BASE).file(upload(file(
                        row("Công ty An Phú", "0301 234 567", "0283 123 4567", "lienhe@anphu.vn", "KCN Tân Tạo"),
                        row("Công ty Bình Minh")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.importedCount").value(2));

        assertThat(supplierRepository.findByTaxCode("0301234567")).isPresent();
        Supplier minimal = supplierRepository.findByNameIgnoreCase("Công ty Bình Minh").orElseThrow();
        assertThat(minimal.getCode()).matches("NCC\\d{3,}");
        assertThat(minimal.getTaxCode()).isNull();
        assertThat(minimal.getEmail()).isNull();
    }

    @Test
    @DisplayName("BR-SUP-09: MST trùng NCC đã có, trùng dòng khác trong file, sai định dạng - lỗi tại cột Mã số thuế")
    void shouldReportTaxCodeProblems() throws Exception {
        Supplier existing = new Supplier();
        existing.setName("Công ty Cũ");
        existing.setTaxCode("0101234567");
        supplierRepository.save(existing);

        mockMvc.perform(multipart(BASE + "/preview").file(upload(file(
                        row("Công ty A", "0101234567"),          // dòng 2: trùng NCC đã có
                        row("Công ty B", "0201234567"),          // dòng 3: hợp lệ
                        row("Công ty C", "0201 234 567"),        // dòng 4: trùng dòng 3 (sau khi bỏ khoảng trắng)
                        row("Công ty D", "12345")))))            // dòng 5: sai định dạng
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validRows").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].field").value("taxCode"))
                .andExpect(jsonPath("$.data.errors[0].column").value("Mã số thuế"))
                .andExpect(jsonPath("$.data.errors[0].message").value(containsString("đã thuộc về nhà cung cấp 'Công ty Cũ'")))
                .andExpect(jsonPath("$.data.errors[1].message").value("Trùng với dòng 3 trong file"))
                .andExpect(jsonPath("$.data.errors[2].row").value(5))
                .andExpect(jsonPath("$.data.errors[2].field").value("taxCode"));
    }

    @Test
    @DisplayName("BR-SUP-03, BR-SUP-04: tên trùng NCC đã ngừng - lỗi kèm hướng dẫn mở lại; email sai - lỗi cột Email")
    void shouldReportDuplicateAndInvalidEmail() throws Exception {
        Supplier inactive = new Supplier();
        inactive.setName("Công ty Cũ");
        inactive.setActive(false);
        supplierRepository.save(inactive);

        mockMvc.perform(multipart(BASE + "/preview").file(upload(file(
                        row("công ty cũ"),
                        row("Công ty Mới", null, null, "khong-phai-email")))))
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
