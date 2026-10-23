package com.grainwarehouse.customer;

import com.grainwarehouse.customer.entity.Customer;
import com.grainwarehouse.customer.entity.CustomerType;
import com.grainwarehouse.customer.repository.CustomerRepository;
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
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhập khách hàng từ Excel (BR-XLS): SĐT chuẩn hóa trước khi so trùng, cả trong file lẫn với DB. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CustomerImportApiTest {

    private static final String BASE = "/api/customers/import";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    @DisplayName("BR-CUS-06: commit - SĐT lưu dạng chuẩn hóa, loại khách đọc từ nhãn tiếng Việt")
    void shouldImportCustomers() throws Exception {
        mockMvc.perform(multipart(BASE).file(upload(file(
                        row("Đại lý Minh Phát", "Đại lý", "0901 234 567", "Anh Phát", "Ấp 3", null),
                        row("Trại heo Bảy Lộc", "Trang trại", "0902.345.678")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.importedCount").value(2));

        Customer dealer = customerRepository.findByPhone("0901234567").orElseThrow();
        assertThat(dealer.getType()).isEqualTo(CustomerType.DEALER);
        assertThat(dealer.getCode()).matches("KH\\d{4,}");
        assertThat(customerRepository.findByPhone("0902345678")).isPresent();
    }

    @Test
    @DisplayName("BR-CUS-04: preview - SĐT trùng DB, trùng trong file (khác cách gõ), sai định dạng, sai loại")
    void shouldReportPhoneProblems() throws Exception {
        Customer existing = new Customer();
        existing.setName("Khách cũ");
        existing.setType(CustomerType.RETAIL);
        existing.setPhone("0909000111");
        customerRepository.save(existing);

        mockMvc.perform(multipart(BASE + "/preview").file(upload(file(
                        row("A", "Bán lẻ", "0909 000 111"),       // dòng 2: trùng khách đã có
                        row("B", "Bán lẻ", "0911222333"),         // dòng 3: hợp lệ
                        row("C", "Bán lẻ", "0911-222-333"),       // dòng 4: trùng dòng 3 (cùng số sau chuẩn hóa)
                        row("D", "Bán lẻ", "12345"),              // dòng 5: sai định dạng
                        row("E", "Siêu thị", "0933444555")))))    // dòng 6: sai loại
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validRows").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].field").value("phone"))
                .andExpect(jsonPath("$.data.errors[0].message").value(containsString("đã thuộc về khách hàng 'Khách cũ'")))
                .andExpect(jsonPath("$.data.errors[1].message").value("Trùng với dòng 3 trong file"))
                .andExpect(jsonPath("$.data.errors[2].row").value(5))
                .andExpect(jsonPath("$.data.errors[2].field").value("phone"))
                .andExpect(jsonPath("$.data.errors[?(@.row == 6)].field").value(hasItems("type")));
    }

    @SafeVarargs
    private byte[] file(List<Object>... rows) throws Exception {
        return ExcelTestFiles.fill(ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/template"), Arrays.asList(rows));
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }
}
