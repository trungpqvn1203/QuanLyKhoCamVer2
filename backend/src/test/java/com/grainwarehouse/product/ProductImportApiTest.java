package com.grainwarehouse.product;

import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import com.grainwarehouse.supplier.entity.Supplier;
import com.grainwarehouse.supplier.repository.ProductSupplierRepository;
import com.grainwarehouse.supplier.repository.SupplierRepository;
import com.grainwarehouse.support.ExcelTestFiles;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.grainwarehouse.support.ExcelTestFiles.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhập sản phẩm từ Excel (BR-XLS) trên DB thật; mỗi test rollback nhờ @Transactional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductImportApiTest {

    private static final String BASE = "/api/products/import";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductUnitRepository unitRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private ProductSupplierRepository productSupplierRepository;

    @BeforeEach
    void setUp() {
        saveCategory("Cám heo", true);
        saveCategory("Cám vịt", false);
    }

    @Test
    @DisplayName("BR-XLS-01: file mẫu có tiêu đề, dropdown danh mục đang dùng, mã mẫu trong sheet ẩn")
    void shouldGenerateTemplate() throws Exception {
        mockMvc.perform(get(BASE + "/template"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, ExcelTestFiles.XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("mau-nhap-san-pham.xlsx")));

        byte[] template = ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/template");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            Row header = workbook.getSheet("Dữ liệu").getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Tên sản phẩm *");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("Tồn tối thiểu");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Nhà cung cấp");

            List<? extends DataValidation> dropdowns = workbook.getSheet("Dữ liệu").getDataValidations();
            assertThat(dropdowns).hasSize(2); // Danh mục, Đơn vị

            Sheet meta = workbook.getSheet("_meta");
            assertThat(workbook.getSheetVisibility(workbook.getSheetIndex(meta))).isEqualTo(SheetVisibility.VERY_HIDDEN);
            assertThat(meta.getRow(0).getCell(0).getStringCellValue()).isEqualTo("PRODUCT_v2");
            // Danh mục đã ngừng dùng không có trong dropdown
            List<String> categories = new ArrayList<>();
            for (Row row : meta) {
                if (row.getCell(2) != null) {
                    categories.add(row.getCell(2).getStringCellValue());
                }
            }
            assertThat(categories).contains("Cám heo").doesNotContain("Cám vịt");
        }
    }

    @Test
    @DisplayName("BR-XLS-02: preview file hợp lệ - báo số dòng, KHÔNG ghi DB")
    void shouldPreviewWithoutSaving() throws Exception {
        long before = productRepository.count();

        preview(file(
                row("Cám heo con 25kg", "Cám heo", "Bao", 25, 50, null, "Heo con"),
                row("Cám heo thịt", "cám HEO", "bao", "40", null, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.validRows").value(2))
                .andExpect(jsonPath("$.data.errors").isEmpty());

        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("BR-XLS-03: preview gom MỌI lỗi, mỗi lỗi có số dòng Excel + cột")
    void shouldCollectAllRowErrors() throws Exception {
        saveProduct("Cám heo nái");

        preview(file(
                row(null, "Cám heo", "Bao", 25),                 // dòng 2: thiếu tên
                row("Cám A", "Cám bò", "Bao", 25),               // dòng 3: danh mục không tồn tại
                row("Cám B", "Cám vịt", "Bao", 25),              // dòng 4: danh mục ngừng dùng
                row("Cám C", "Cám heo", "Tấn", "25kg"),           // dòng 5: đơn vị không tồn tại + khối lượng không phải số
                row("cám heo NÁI", "Cám heo", "Bao", 25),        // dòng 6: trùng SP đã có trong DB
                row("Cám D", "Cám heo", "Kg", 25),               // dòng 7: đơn vị Kg phải 1 kg (BR-UNIT-03)
                row("Cám E", "Cám heo", "Bao", 25, 10.5),        // dòng 8: tồn tối thiểu không nguyên
                row("Cám F", "Cám heo", "Bao", 25),              // dòng 9: hợp lệ
                row("cám f ", "Cám heo", "Bao", 25)))            // dòng 10: trùng dòng 9 trong file
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(9))
                .andExpect(jsonPath("$.data.validRows").value(1))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].field").value("name"))
                .andExpect(jsonPath("$.data.errors[0].column").value("Tên sản phẩm"))
                .andExpect(jsonPath("$.data.errors[1].message").value("Danh mục 'Cám bò' không tồn tại"))
                .andExpect(jsonPath("$.data.errors[2].message").value("Danh mục 'Cám vịt' đã ngừng dùng"))
                .andExpect(jsonPath("$.data.errors[?(@.row == 5)].field").value(hasItems("unitId", "netWeightKg")))
                .andExpect(jsonPath("$.data.errors[?(@.row == 6)].message")
                        .value(hasItems(containsString("đã tồn tại"))))
                .andExpect(jsonPath("$.data.errors[?(@.row == 7)].message")
                        .value(hasItems("Sản phẩm đơn vị Kg phải có khối lượng tịnh bằng 1 kg")))
                .andExpect(jsonPath("$.data.errors[?(@.row == 8)].field").value(hasItems("minStockQuantity")))
                .andExpect(jsonPath("$.data.errors[?(@.row == 10)].message")
                        .value(hasItems("Trùng với dòng 9 trong file")));
    }

    @Test
    @DisplayName("BR-XLS-02: commit file hợp lệ - 201, mọi dòng được tạo với mã tự sinh; Kg để trống khối lượng = 1")
    void shouldImportAllRows() throws Exception {
        long before = productRepository.count();

        mockMvc.perform(multipart(BASE).file(upload(file(
                        row("Cám heo con 25kg", "Cám heo", "Bao", 25, 50, null, "Heo con"),
                        row("Cám xá", "Cám heo", "Kg", null)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.importedCount").value(2))
                .andExpect(jsonPath("$.message").value("Đã nhập 2 sản phẩm"));

        assertThat(productRepository.count()).isEqualTo(before + 2);
        Product bulk = productRepository.findByNameIgnoreCase("Cám xá").orElseThrow();
        assertThat(bulk.getCode()).matches("SP\\d{6}");
        assertThat(bulk.getUnit().getName()).isEqualTo("Kg");
        assertThat(bulk.getNetWeightKg()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("BR-XLS-02: commit có một dòng lỗi - 400, KHÔNG dòng nào được ghi (kể cả dòng đúng)")
    void shouldImportNothingWhenAnyRowFails() throws Exception {
        long before = productRepository.count();

        mockMvc.perform(multipart(BASE).file(upload(file(
                        row("Cám đúng 1", "Cám heo", "Bao", 25),
                        row("Cám đúng 2", "Cám heo", "Bao", 25),
                        row("Cám sai", "Cám heo", "Bao", -5)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("File có 1 lỗi, chưa nhập dòng nào. Hãy kiểm tra lại file"))
                .andExpect(jsonPath("$.error.details[0].field").value("rows[4].netWeightKg"));

        assertThat(productRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("BR-XLS-05: file không phải mẫu sản phẩm (mẫu khách hàng, file tự tạo, không phải Excel) - 400 field file")
    void shouldRejectWrongFiles() throws Exception {
        byte[] customerTemplate = ExcelTestFiles.downloadTemplate(mockMvc, "/api/customers/import/template");
        preview(customerTemplate)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("file"))
                .andExpect(jsonPath("$.error.details[0].message").value(containsString("không phải mẫu nhập sản phẩm")));

        preview(ExcelTestFiles.foreignWorkbook())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("file"));

        preview("ten,danh muc\nCam,Cam heo".getBytes())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].message").value(containsString("Không đọc được file")));
    }

    @Test
    @DisplayName("BR-XLS-04: file mẫu chưa điền dòng nào - 400; quá 1000 dòng - 400")
    void shouldRejectEmptyAndOversizedFiles() throws Exception {
        preview(ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/template"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].message").value(containsString("chưa có dòng dữ liệu")));

        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            rows.add(row("Cám " + i, "Cám heo", "Bao", 25));
        }
        preview(ExcelTestFiles.fill(ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/template"), rows))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].message").value(containsString("quá 1000 dòng")));
    }

    @Test
    @DisplayName("Đổi tiêu đề cột trong file mẫu - 400 chỉ rõ cột")
    void shouldRejectRenamedHeader() throws Exception {
        byte[] template = ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/template");
        byte[] renamed;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.getSheet("Dữ liệu").getRow(0).getCell(2).setCellValue("ĐVT");
            workbook.write(out);
            renamed = out.toByteArray();
        }

        preview(ExcelTestFiles.fill(renamed, List.of(row("Cám", "Cám heo", "Bao", 25))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].message").value(containsString("Cột C phải có tiêu đề 'Đơn vị *'")));
    }

    @Test
    @DisplayName("BR-PRD-10: cột Nhà cung cấp - nhiều mã cách nhau bằng ; (không phân biệt hoa thường), gán khi nhập")
    void shouldLinkSuppliersByCode() throws Exception {
        Supplier first = saveSupplier("NCC nhập Excel 1", true);
        Supplier second = saveSupplier("NCC nhập Excel 2", true);

        mockMvc.perform(multipart(BASE).file(upload(file(
                        row("Cám heo có NCC", "Cám heo", "Bao", 25, null,
                                first.getCode().toLowerCase() + " ;" + second.getCode() + ";")))))
                .andExpect(status().isCreated());

        Long productId = productRepository.findByNameIgnoreCase("Cám heo có NCC").orElseThrow().getId();
        assertThat(productSupplierRepository.findByIdProductId(productId))
                .extracting(link -> link.getId().getSupplierId())
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    @DisplayName("BR-PRD-10: mã NCC không tồn tại - lỗi tại cột Nhà cung cấp; NCC ngừng hoạt động - lỗi của dòng")
    void shouldReportSupplierErrors() throws Exception {
        Supplier stopped = saveSupplier("NCC nhập Excel đã ngừng", false);

        preview(file(
                row("Cám A", "Cám heo", "Bao", 25, null, "NCC99999"),
                row("Cám B", "Cám heo", "Bao", 25, null, stopped.getCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validRows").value(0))
                .andExpect(jsonPath("$.data.errors[0].row").value(2))
                .andExpect(jsonPath("$.data.errors[0].field").value("supplierIds"))
                .andExpect(jsonPath("$.data.errors[0].message").value("Nhà cung cấp mã 'NCC99999' không tồn tại"))
                .andExpect(jsonPath("$.data.errors[1].row").value(3))
                .andExpect(jsonPath("$.data.errors[1].message").value(containsString("đang ngừng hoạt động")));
    }

    @Test
    @DisplayName("Không gửi file - 400 field file")
    void shouldRequireFile() throws Exception {
        mockMvc.perform(multipart(BASE + "/preview"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("file"));
    }

    private ResultActions preview(byte[] content) throws Exception {
        return mockMvc.perform(multipart(BASE + "/preview").file(upload(content)));
    }

    @SafeVarargs
    private byte[] file(List<Object>... rows) throws Exception {
        return ExcelTestFiles.fill(ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/template"), Arrays.asList(rows));
    }

    /** Arrays.asList thay vì List.of: List.of không nhận null (ô trống). */
    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    private Supplier saveSupplier(String name, boolean active) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setActive(active);
        // saveAndFlush: đọc lại mã NCC do DB tự sinh
        return supplierRepository.saveAndFlush(supplier);
    }

    private void saveCategory(String name, boolean active) {
        Category category = new Category();
        category.setName(name);
        category.setActive(active);
        categoryRepository.save(category);
    }

    private void saveProduct(String name) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(categoryRepository.findByNameIgnoreCase("Cám heo").orElseThrow());
        product.setUnit(unitRepository.findByNameIgnoreCase("Bao").orElseThrow());
        product.setNetWeightKg(BigDecimal.valueOf(25));
        productRepository.save(product);
    }
}
