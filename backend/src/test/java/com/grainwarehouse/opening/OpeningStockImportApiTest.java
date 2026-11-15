package com.grainwarehouse.opening;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grainwarehouse.inventory.repository.StockDocumentRepository;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import com.grainwarehouse.product.repository.ProductUnitRepository;
import com.grainwarehouse.support.ExcelTestFiles;
import com.grainwarehouse.warehouse.entity.Warehouse;
import com.grainwarehouse.warehouse.entity.WarehouseType;
import com.grainwarehouse.warehouse.repository.WarehouseRepository;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static com.grainwarehouse.support.ExcelTestFiles.upload;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nhập tồn đầu kỳ từ Excel (BR-OPN-Q01, BR-XLS) trên DB thật; mỗi test rollback nhờ @Transactional. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OpeningStockImportApiTest {

    private static final String BASE = "/api/opening-stocks";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductUnitRepository unitRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private StockDocumentRepository stockDocumentRepository;

    private Category category;
    private Warehouse warehouse;
    private Product activeA;
    private Product activeB;
    private Product stopped;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Cám test " + System.nanoTime());
        categoryRepository.save(category);

        warehouse = saveWarehouse("Kho trung tâm", true);
        activeA = saveProduct("Cám heo con 25kg", true);
        activeB = saveProduct("Cám vịt đẻ 40kg", true);
        stopped = saveProduct("Cám ngừng bán", false);
    }

    @Test
    @DisplayName("BR-XLS-01: file mẫu có tiêu đề, dropdown SP đang kinh doanh, mã mẫu trong sheet ẩn")
    void shouldGenerateTemplate() throws Exception {
        mockMvc.perform(get(BASE + "/import/template"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, ExcelTestFiles.XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("mau-nhap-ton-dau-ky.xlsx")));

        byte[] template = ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/import/template");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            Row header = workbook.getSheet("Dữ liệu").getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Sản phẩm *");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Số lượng tồn *");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Đơn giá");

            List<? extends DataValidation> dropdowns = workbook.getSheet("Dữ liệu").getDataValidations();
            assertThat(dropdowns).hasSize(1);

            List<String> prefilledProducts = new java.util.ArrayList<>();
            var sheet = workbook.getSheet("Dữ liệu");
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                prefilledProducts.add(sheet.getRow(r).getCell(0).getStringCellValue());
            }
            assertThat(prefilledProducts).contains(
                    activeA.getCode() + " · " + activeA.getName(),
                    activeB.getCode() + " · " + activeB.getName());
            assertThat(prefilledProducts).noneMatch(label -> label.startsWith(stopped.getCode() + " "));

            int metaIdx = workbook.getSheetIndex("_meta");
            assertThat(workbook.getSheetVisibility(metaIdx)).isEqualTo(SheetVisibility.VERY_HIDDEN);
            assertThat(workbook.getSheetAt(metaIdx).getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("OPENING_STOCK_v1");
        }
    }

    @Test
    @DisplayName("Dòng sản phẩm điền sẵn nhưng để trống Số lượng và Đơn giá thì bỏ qua; không điền dòng nào thì báo lỗi")
    void shouldSkipUntouchedPrefilledRows() throws Exception {
        byte[] template = ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/import/template");

        // Không điền số lượng cho sản phẩm nào → báo lỗi mức file
        mockMvc.perform(multipart(BASE + "/import/preview").file(upload(template)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("file"));

        // Chỉ điền số lượng cho dòng activeA trong file mẫu có sẵn danh sách SP
        byte[] filled;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             var out = new java.io.ByteArrayOutputStream()) {
            var sheet = workbook.getSheet("Dữ liệu");
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row.getCell(0).getStringCellValue().startsWith(activeA.getCode() + " ")) {
                    row.createCell(1).setCellValue(35);
                }
            }
            workbook.write(out);
            filled = out.toByteArray();
        }

        mockMvc.perform(multipart(BASE + "/import/preview").file(upload(filled)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(1))
                .andExpect(jsonPath("$.data.validRows").value(1))
                .andExpect(jsonPath("$.data.errors").isEmpty());

        mockMvc.perform(multipart(BASE + "/import")
                        .file(upload(filled))
                        .param("warehouseId", String.valueOf(warehouse.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lineCount").value(1))
                .andExpect(jsonPath("$.data.totalQuantity").value(35))
                .andExpect(jsonPath("$.data.lines[0].productId").value(activeA.getId()));
    }

    @Test
    @DisplayName("Cho phép nhập số lượng tồn đầu kỳ âm và xác nhận tạo tồn âm")
    void shouldAllowNegativeOpeningStock() throws Exception {
        byte[] template = ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/import/template");
        byte[] file = ExcelTestFiles.fill(template, List.of(
                Arrays.asList(activeA.getCode() + " · " + activeA.getName(), -20, 100000)
        ));

        String createdJson = mockMvc.perform(multipart(BASE + "/import")
                        .file(upload(file))
                        .param("warehouseId", String.valueOf(warehouse.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalQuantity").value(-20))
                .andExpect(jsonPath("$.data.totalAmount").value(-2000000))
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(createdJson).get("data").get("id").asLong();
        mockMvc.perform(post(BASE + "/{id}/complete", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("Preview kiểm tra toàn bộ dòng, gom lỗi theo dòng và cột, không tạo phiếu")
    void shouldPreviewAndCollectAllErrors() throws Exception {
        byte[] template = ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/import/template");
        byte[] file = ExcelTestFiles.fill(template, List.of(
                Arrays.asList(activeA.getCode() + " · " + activeA.getName(), 50, 250000),
                Arrays.asList(activeA.getCode(), 10, null), // trùng SP với dòng 2
                Arrays.asList("SP_KHONG_TON_TAI", 0, -5), // SP không tồn tại, SL = 0, đơn giá âm
                Arrays.asList(stopped.getCode(), 12.5, null) // SP ngừng kinh doanh, SL không nguyên
        ));

        long beforeCount = stockDocumentRepository.count();

        mockMvc.perform(multipart(BASE + "/import/preview").file(upload(file)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(4))
                .andExpect(jsonPath("$.data.validRows").value(1))
                .andExpect(jsonPath("$.data.errors[*].row", hasItems(3, 4, 5)))
                .andExpect(jsonPath("$.data.errors[*].message", hasItems(
                        containsString("Trùng với dòng 2"),
                        containsString("không tồn tại"),
                        containsString("khác 0"),
                        containsString("không được âm"),
                        containsString("ngừng kinh doanh"),
                        containsString("số nguyên")
                )));

        assertThat(stockDocumentRepository.count()).isEqualTo(beforeCount);
    }

    @Test
    @DisplayName("Preview từ chối file không phải mẫu của hệ thống")
    void shouldRejectForeignWorkbook() throws Exception {
        mockMvc.perform(multipart(BASE + "/import/preview").file(upload(ExcelTestFiles.foreignWorkbook())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details[0].field").value("file"));
    }

    @Test
    @DisplayName("Nhập Excel tạo phiếu tồn đầu kỳ Nháp mới với đầy đủ dòng")
    void shouldImportNewDraft() throws Exception {
        byte[] template = ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/import/template");
        byte[] file = ExcelTestFiles.fill(template, List.of(
                Arrays.asList(activeA.getCode() + " · " + activeA.getName(), 100, 250000),
                Arrays.asList(activeB.getCode(), 40, null)
        ));

        mockMvc.perform(multipart(BASE + "/import")
                        .file(upload(file))
                        .param("warehouseId", String.valueOf(warehouse.getId()))
                        .param("documentDate", "2026-10-31")
                        .param("note", "Nhập tồn đầu kỳ từ Excel"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(matchesPattern("^DTK\\d{6}$")))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.warehouseId").value(warehouse.getId()))
                .andExpect(jsonPath("$.data.documentDate").value("2026-10-31"))
                .andExpect(jsonPath("$.data.note").value("Nhập tồn đầu kỳ từ Excel"))
                .andExpect(jsonPath("$.data.lineCount").value(2))
                .andExpect(jsonPath("$.data.totalQuantity").value(140))
                .andExpect(jsonPath("$.data.lines[0].productId").value(activeA.getId()))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(100))
                .andExpect(jsonPath("$.data.lines[1].productId").value(activeB.getId()))
                .andExpect(jsonPath("$.data.lines[1].unitPrice").value(0));
    }

    @Test
    @DisplayName("Nhập Excel có dòng lỗi thì không tạo phiếu")
    void shouldNotCreateDraftWhenFileHasErrors() throws Exception {
        byte[] template = ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/import/template");
        byte[] file = ExcelTestFiles.fill(template, List.of(
                Arrays.asList(activeA.getCode(), 100, 250000),
                Arrays.asList(activeB.getCode(), 0, null)
        ));

        long beforeCount = stockDocumentRepository.count();

        mockMvc.perform(multipart(BASE + "/import")
                        .file(upload(file))
                        .param("warehouseId", String.valueOf(warehouse.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("rows[3].quantity"));

        assertThat(stockDocumentRepository.count()).isEqualTo(beforeCount);
    }

    @Test
    @DisplayName("BR-OPN-Q01: nhập Excel thay toàn bộ dòng của phiếu Nháp đang có; từ chối nếu đã Hoàn tất")
    void shouldReplaceDraftLinesAndRejectCompleted() throws Exception {
        String createBody = """
                {"warehouseId": %d, "lines": [{"productId": %d, "quantity": 10}]}
                """.formatted(warehouse.getId(), activeA.getId());
        String createdJson = mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long draftId = objectMapper.readTree(createdJson).get("data").get("id").asLong();

        byte[] template = ExcelTestFiles.downloadTemplate(mockMvc, BASE + "/import/template");
        byte[] file = ExcelTestFiles.fill(template, List.of(
                Arrays.asList(activeB.getCode() + " · " + activeB.getName(), 75, 180000)
        ));

        mockMvc.perform(multipart(BASE + "/{id}/import", draftId).file(upload(file)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(draftId))
                .andExpect(jsonPath("$.data.lineCount").value(1))
                .andExpect(jsonPath("$.data.totalQuantity").value(75))
                .andExpect(jsonPath("$.data.lines[0].productId").value(activeB.getId()));

        mockMvc.perform(post(BASE + "/{id}/complete", draftId))
                .andExpect(status().isOk());

        mockMvc.perform(multipart(BASE + "/{id}/import", draftId).file(upload(file)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message", containsString("Nháp")));
    }

    private Product saveProduct(String name, boolean active) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setUnit(unitRepository.findByNameIgnoreCase("Bao").orElseThrow());
        product.setNetWeightKg(new BigDecimal("25"));
        product.setActive(active);
        return productRepository.save(product);
    }

    private Warehouse saveWarehouse(String name, boolean active) {
        Warehouse w = new Warehouse();
        w.setName(name);
        w.setType(WarehouseType.CENTRAL);
        w.setAddress("Địa chỉ test");
        w.setActive(active);
        return warehouseRepository.save(w);
    }
}
