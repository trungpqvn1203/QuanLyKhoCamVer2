package com.grainwarehouse.support;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tạo file Excel cho test nhập hàng loạt theo đúng đường người dùng đi: tải file mẫu từ API → điền dòng → upload.
 * Nhờ vậy test kiểm luôn cả việc file mẫu do backend sinh ra đọc lại được.
 */
public final class ExcelTestFiles {

    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelTestFiles() {
    }

    public static byte[] downloadTemplate(MockMvc mockMvc, String url) throws Exception {
        return mockMvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    /**
     * Điền các dòng vào sheet "Dữ liệu" từ dòng 2. Phần tử Number → ô số (như người dùng gõ số trong Excel),
     * String → ô chữ, null → để trống.
     */
    public static byte[] fill(byte[] template, List<List<Object>> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = workbook.getSheet("Dữ liệu");
            for (int r = 0; r < rows.size(); r++) {
                Row row = data.createRow(r + 1);
                List<Object> values = rows.get(r);
                for (int c = 0; c < values.size(); c++) {
                    Object value = values.get(c);
                    if (value instanceof Number number) {
                        row.createCell(c).setCellValue(number.doubleValue());
                    } else if (value != null) {
                        row.createCell(c).setCellValue(value.toString());
                    }
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** File .xlsx hợp lệ nhưng không phải mẫu của hệ thống (người dùng tự tạo file). */
    public static byte[] foreignWorkbook() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.createSheet("Sheet1").createRow(0).createCell(0).setCellValue("Tên sản phẩm");
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public static MockMultipartFile upload(byte[] content) {
        return new MockMultipartFile("file", "du-lieu.xlsx", XLSX, content);
    }
}
