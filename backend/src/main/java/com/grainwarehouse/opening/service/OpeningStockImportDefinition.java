package com.grainwarehouse.opening.service;

import com.grainwarehouse.common.importing.ImportColumn;
import com.grainwarehouse.common.importing.ImportDefinition;
import com.grainwarehouse.common.importing.ImportRow;
import com.grainwarehouse.opening.dto.OpeningStockLineRequest;
import com.grainwarehouse.product.entity.Product;
import com.grainwarehouse.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Mẫu nhập dòng tồn đầu kỳ từ Excel (BR-OPN-Q01, BR-XLS).
 * Sản phẩm ghi theo "mã · tên" (hoặc chỉ gõ mã) — người dùng không cần biết id.
 * Kiểm tra kho & lưu phiếu nháp do {@link OpeningStockService} thực hiện theo ngữ cảnh phiếu.
 */
@Component
@RequiredArgsConstructor
public class OpeningStockImportDefinition implements ImportDefinition<OpeningStockLineRequest, OpeningStockLineRequest> {

    private static final String PRODUCT_LABEL_SEPARATOR = " · ";

    private final ProductRepository productRepository;

    @Override
    public String templateId() {
        return "OPENING_STOCK_v1";
    }

    @Override
    public String entityLabel() {
        return "tồn đầu kỳ";
    }

    @Override
    public String fileName() {
        return "mau-nhap-ton-dau-ky.xlsx";
    }

    @Override
    public List<ImportColumn> columns() {
        List<String> products = activeProductLabels();
        return List.of(
                ImportColumn.required("productId", "Sản phẩm",
                                "Đã điền sẵn sản phẩm đang kinh doanh; dòng để trống Số lượng và Đơn giá sẽ bỏ qua")
                        .withOptions(products),
                ImportColumn.required("quantity", "Số lượng tồn",
                        "Số nguyên khác 0 (có thể âm nếu đã xuất trước khi nhập)"),
                ImportColumn.optional("unitPrice", "Đơn giá", "Không bắt buộc (để trống = 0), chỉ để tham khảo"));
    }

    @Override
    public List<List<String>> prefilledRows() {
        return activeProductLabels().stream().map(List::of).toList();
    }

    @Override
    public boolean shouldSkipRow(ImportRow row) {
        return !row.hasValue("quantity") && !row.hasValue("unitPrice");
    }

    private List<String> activeProductLabels() {
        return productRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(product -> product.getCode() + PRODUCT_LABEL_SEPARATOR + product.getName())
                .toList();
    }

    @Override
    public List<String> example() {
        return List.of("SP0001 · Cám heo con 25kg", "100", "250000");
    }

    @Override
    public OpeningStockLineRequest parse(ImportRow row) {
        Integer quantity = row.integer("quantity");
        if (quantity != null && quantity == 0) {
            row.error("quantity", "Số lượng phải khác 0 (để trống nếu không có tồn đầu kỳ)");
        }
        return new OpeningStockLineRequest(productId(row), quantity, row.decimal("unitPrice"));
    }

    private Long productId(ImportRow row) {
        String cell = row.text("productId");
        if (cell == null) {
            return null;
        }
        String code = cell.split(PRODUCT_LABEL_SEPARATOR, 2)[0].trim();
        Product product = productRepository.findByCodeIgnoreCase(code).orElse(null);
        if (product == null) {
            row.error("productId", "Sản phẩm mã '" + code + "' không tồn tại");
            return null;
        }
        if (!product.isActive()) {
            row.error("productId", "Sản phẩm '" + product.getName() + "' (" + product.getCode()
                    + ") đã ngừng kinh doanh");
            return null;
        }
        return product.getId();
    }

    @Override
    public Map<String, String> duplicateKeys(OpeningStockLineRequest request) {
        return request.productId() == null
                ? Map.of()
                : Map.of("productId", String.valueOf(request.productId()));
    }

    @Override
    public OpeningStockLineRequest prepare(OpeningStockLineRequest request) {
        return request;
    }

    @Override
    public void saveAll(List<OpeningStockLineRequest> rows) {
        throw new UnsupportedOperationException("Dùng OpeningStockService để lưu phiếu tồn đầu kỳ");
    }
}

