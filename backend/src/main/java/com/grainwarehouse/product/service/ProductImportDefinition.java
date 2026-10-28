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
import com.grainwarehouse.product.repository.ProductUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mẫu nhập sản phẩm (BR-XLS). Danh mục và đơn vị ghi bằng TÊN, NCC chọn "mã · tên" — người dùng không biết id.
 * Entity đã kiểm tra là {@link PreparedProduct}: SP chưa lưu + NCC cần gán sau khi SP có id (BR-PRD-10).
 */
@Component
@RequiredArgsConstructor
public class ProductImportDefinition implements ImportDefinition<CreateProductRequest, ProductImportDefinition.PreparedProduct> {

    /**
     * Dropdown Excel chỉ chọn được MỘT giá trị mỗi ô → tối đa 3 cột NCC, mỗi cột một dropdown.
     * SP nhiều NCC hơn thì gán thêm trên màn hình.
     */
    private static final List<String> SUPPLIER_COLUMNS = List.of("supplier1", "supplier2", "supplier3");
    /** Nhãn dropdown "NCC001 · Công ty A"; khi đọc chỉ lấy phần mã trước dấu này. */
    private static final String SUPPLIER_LABEL_SEPARATOR = " · ";

    public record PreparedProduct(Product product, Set<Long> supplierIds) {
    }

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductUnitRepository unitRepository;
    private final ProductSupplierLinks supplierLinks;

    @Override
    public String templateId() {
        // v2 (2026-10-28): thêm cột Nhà cung cấp → file mẫu cũ bị từ chối, tải mẫu mới
        return "PRODUCT_v2";
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
        List<String> units = unitRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(ProductUnit::getName)
                .toList();
        List<String> suppliers = supplierLinks.activeSuppliers().stream()
                .map(supplier -> supplier.code() + SUPPLIER_LABEL_SEPARATOR + supplier.name())
                .toList();
        List<ImportColumn> columns = new ArrayList<>(List.of(
                ImportColumn.required("name", "Tên sản phẩm", "Tối đa 200 ký tự, không trùng sản phẩm đã có"),
                ImportColumn.required("categoryId", "Danh mục", "Chọn trong danh sách danh mục đang dùng")
                        .withOptions(categories),
                ImportColumn.required("unitId", "Đơn vị", "Chọn trong danh sách đơn vị đang dùng")
                        .withOptions(units),
                ImportColumn.required("netWeightKg", "Khối lượng tịnh (kg)",
                        "Khối lượng một đơn vị: bao 25kg ghi 25. Đơn vị Kg ghi 1 hoặc để trống"),
                ImportColumn.optional("minStockQuantity", "Tồn tối thiểu",
                        "Số nguyên ≥ 0; để trống = không cảnh báo sắp hết")));
        for (int i = 0; i < SUPPLIER_COLUMNS.size(); i++) {
            columns.add(ImportColumn.optional(SUPPLIER_COLUMNS.get(i), "Nhà cung cấp " + (i + 1),
                            "Chọn NCC đang hoạt động; để trống = không gán. Hơn 3 NCC thì gán thêm trên màn hình")
                    .withOptions(suppliers));
        }
        columns.add(ImportColumn.optional("description", "Mô tả", "Tối đa 1000 ký tự"));
        return columns;
    }

    @Override
    public List<String> example() {
        return List.of("Cám heo con 25kg", "Cám heo", "Bao", "25", "50", "NCC001 · Công ty A", "", "",
                "Cho heo con 7–15kg");
    }

    @Override
    public CreateProductRequest parse(ImportRow row) {
        ProductUnit unit = unit(row);
        BigDecimal netWeightKg = row.decimal("netWeightKg");
        // BR-XLS-A02: đơn vị có khối lượng cố định (Kg = 1) mà để trống → lấy giá trị đó, giống form nhập tay
        if (unit != null && unit.getFixedNetWeightKg() != null && netWeightKg == null
                && !row.hasError("netWeightKg")) {
            netWeightKg = unit.getFixedNetWeightKg();
        }
        return new CreateProductRequest(row.text("name"), categoryId(row), unit == null ? null : unit.getId(),
                netWeightKg, row.text("description"), row.integer("minStockQuantity"), supplierIds(row));
    }

    /**
     * "NCC001 · Công ty A" (hoặc chỉ gõ "ncc001") ở các cột NCC → id; chọn trùng hai cột thì bỏ trùng ở prepare().
     * Mã không tồn tại → lỗi tại đúng cột; NCC ngừng hoạt động do prepare() báo (BR-SUP-06).
     */
    private List<Long> supplierIds(ImportRow row) {
        List<Long> ids = new ArrayList<>();
        for (String column : SUPPLIER_COLUMNS) {
            String cell = row.text(column);
            if (cell == null) {
                continue;
            }
            // Tên NCC có thể đổi sau khi tải mẫu → chỉ tin phần mã
            String code = cell.split(SUPPLIER_LABEL_SEPARATOR, 2)[0].trim();
            Optional<Long> id = supplierLinks.findSupplierIdByCode(code);
            if (id.isEmpty()) {
                row.error(column, "Nhà cung cấp mã '" + code + "' không tồn tại");
            } else {
                ids.add(id.get());
            }
        }
        return ids;
    }

    /** Tên đơn vị → bản ghi. Không tìm thấy / đã ngừng dùng → lỗi ngay tại cột Đơn vị. */
    private ProductUnit unit(ImportRow row) {
        String name = row.text("unitId");
        if (name == null) {
            return null;
        }
        ProductUnit unit = unitRepository.findByNameIgnoreCase(name).orElse(null);
        if (unit == null) {
            row.error("unitId", "Đơn vị '" + name + "' không tồn tại");
            return null;
        }
        if (!unit.isActive()) {
            row.error("unitId", "Đơn vị '" + unit.getName() + "' đã ngừng dùng");
            return null;
        }
        return unit;
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
    public PreparedProduct prepare(CreateProductRequest request) {
        Set<Long> supplierIds = request.supplierIds() == null ? Set.of() : new LinkedHashSet<>(request.supplierIds());
        return new PreparedProduct(productService.prepareNew(request), supplierIds);
    }

    @Override
    public void saveAll(List<PreparedProduct> rows) {
        productRepository.saveAll(rows.stream().map(PreparedProduct::product).toList());
        // Sau saveAll SP đã có id → tạo liên kết NCC trong cùng transaction nhập file (tất cả hoặc không gì)
        rows.forEach(row -> supplierLinks.sync(row.product(), row.supplierIds()));
    }
}
