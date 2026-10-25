package com.grainwarehouse.product.service;

import com.grainwarehouse.common.importing.ImportColumn;
import com.grainwarehouse.common.importing.ImportDefinition;
import com.grainwarehouse.common.importing.ImportRow;
import com.grainwarehouse.product.dto.CreateProductRequest;
import com.grainwarehouse.product.entity.Category;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.entity.ProductUnit;
import com.grainwarehouse.product.repository.CategoryRepository;
import com.grainwarehouse.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mẫu nhập sản phẩm (BR-XLS). Danh mục ghi bằng TÊN — người dùng không biết id. */
@Component
@RequiredArgsConstructor
public class ProductImportDefinition implements ImportDefinition<CreateProductRequest, Product> {

    // Nhãn giống giao diện (PRODUCT_UNIT_LABELS ở frontend)
    // LinkedHashMap: giữ thứ tự trong dropdown
    private static final Map<String, ProductUnit> UNITS = new LinkedHashMap<>();

    static {
        UNITS.put("Bao", ProductUnit.BAG);
        UNITS.put("Kg", ProductUnit.KG);
    }

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public String templateId() {
        return "PRODUCT_v1";
    }

    @Override
    public String entityLabel() {
        return "sản phẩm";
    }

    @Override
    public String fileName() {
        return "mau-nhap-san-pham.xlsx";
    }

    @Override
    public List<ImportColumn> columns() {
        List<String> categories = categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(Category::getName)
                .toList();
        return List.of(
                ImportColumn.required("name", "Tên sản phẩm", "Tối đa 200 ký tự, không trùng sản phẩm đã có"),
                ImportColumn.required("categoryId", "Danh mục", "Chọn trong danh sách danh mục đang dùng")
                        .withOptions(categories),
                ImportColumn.required("unit", "Đơn vị", "Bao hoặc Kg").withOptions(List.copyOf(UNITS.keySet())),
                ImportColumn.required("netWeightKg", "Khối lượng tịnh (kg)",
                        "Khối lượng một đơn vị: bao 25kg ghi 25. Đơn vị Kg ghi 1 hoặc để trống"),
                ImportColumn.optional("minStockQuantity", "Tồn tối thiểu",
                        "Số nguyên ≥ 0; để trống = không cảnh báo sắp hết"),
                ImportColumn.optional("description", "Mô tả", "Tối đa 1000 ký tự"));
    }

    @Override
    public List<String> example() {
        return List.of("Cám heo con 25kg", "Cám heo", "Bao", "25", "50", "Cho heo con 7–15kg");
    }

    @Override
    public CreateProductRequest parse(ImportRow row) {
        ProductUnit unit = row.option("unit", UNITS);
        BigDecimal netWeightKg = row.decimal("netWeightKg");
        // BR-PRD-06: đơn vị Kg thì khối lượng luôn là 1 — form nhập tay cũng tự điền 1
        if (unit == ProductUnit.KG && netWeightKg == null && !row.hasError("netWeightKg")) {
            netWeightKg = BigDecimal.ONE;
        }
        return new CreateProductRequest(row.text("name"), categoryId(row), unit, netWeightKg,
                row.text("description"), row.integer("minStockQuantity"));
    }

    /** Tên danh mục → id. Không tìm thấy / đã ngừng dùng → lỗi ngay tại cột Danh mục. */
    private Long categoryId(ImportRow row) {
        String name = row.text("categoryId");
        if (name == null) {
            return null;
        }
        Category category = categoryRepository.findByNameIgnoreCase(name).orElse(null);
        if (category == null) {
            row.error("categoryId", "Danh mục '" + name + "' không tồn tại");
            return null;
        }
        if (!category.isActive()) {
            row.error("categoryId", "Danh mục '" + category.getName() + "' đã ngừng dùng");
            return null;
        }
        return category.getId();
    }

    @Override
    public Map<String, String> duplicateKeys(CreateProductRequest request) {
        return Map.of("name", ImportRow.normalizeKey(request.name()));
    }

    @Override
    public Product prepare(CreateProductRequest request) {
        return productService.prepareNew(request);
    }

    @Override
    public void saveAll(List<Product> products) {
        productRepository.saveAll(products);
    }
}
