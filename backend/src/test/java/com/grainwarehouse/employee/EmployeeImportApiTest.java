package com.grainwarehouse.employee;

import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.employee.repository.EmployeeRepository;
import com.grainwarehouse.support.ExcelTestFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static com.grainwarehouse.support.ExcelTestFiles.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhập nhân viên từ Excel (BR-XLS). Tiền là BigDecimal, không làm tròn ngầm (quy tắc cứng 3). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmployeeImportApiTest {

    private static final String BASE = "/api/employees/import";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    @DisplayName("commit - đơn giá đọc đúng giá trị hiển thị trong Excel, SĐT chuẩn hóa")
    void shouldImportEmployees() throws Exception {
        long before = employeeRepository.count();

        mockMvc.perform(multipart(BASE).file(upload(file(
                        row("Nguyễn Văn Bốc", "0912 345 678", 300000, 25000.1, 30000),
                        row("Trần Văn Xếp", null, null, 30000)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.importedCount").value(2));

        assertThat(employeeRepository.count()).isEqualTo(before + 2);
        Employee first = employeeRepository.findAll().stream()
                .filter(e -> e.getName().equals("Nguyễn Văn Bốc")).findFirst().orElseThrow();
        assertThat(first.getPhone()).isEqualTo("0912345678");
        assertThat(first.getUnloadTonRate()).isEqualByComparingTo(new BigDecimal("25000.1"));
        assertThat(first.getDeliveryTonRate()).isEqualByComparingTo(new BigDecimal("30000"));
    }

    @Test
    @DisplayName("BR-EMP-02, BR-XLS-A01: thiếu cả hai đơn giá; 3 chữ số thập phân; trùng tên + SĐT trong file")
    void shouldReportEmployeeProblems() throws Exception {
        mockMvc.perform(multipart(BASE + "/preview").file(upload(file(
                        row("A", null, null, null),               // dòng 2: thiếu cả hai đơn giá
                        row("B", null, 300000.125),               // dòng 3: quá 2 chữ số thập phân
                        row("C", "0912345678", 300000),           // dòng 4: hợp lệ
                        row("c", "0912 345 678", 300000),         // dòng 5: trùng dòng 4
                        row("C", null, 300000)))))                // dòng 6: cùng tên, khác SĐT → hợp lệ
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validRows").value(2))
                .andExpect(jsonPath("$.data.errors[0].field").value("dailyWage"))
                .andExpect(jsonPath("$.data.errors[1].row").value(3))
                .andExpect(jsonPath("$.data.errors[1].field").value("dailyWage"))
                .andExpect(jsonPath("$.data.errors[2].message").value("Trùng với dòng 4 trong file"));
    }

    @SafeVarargs
    private byte[] file(List<Object>... rows) throws Exception {
        return ExcelTestFiles.fill(ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/template"), Arrays.asList(rows));
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }
}
