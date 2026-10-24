package com.grainwarehouse.product.controller;

import com.grainwarehouse.common.importing.ExcelImportService;
import com.grainwarehouse.common.importing.ImportPreview;
import com.grainwarehouse.common.importing.ImportResult;
import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.product.dto.CreateProductRequest;
import com.grainwarehouse.product.dto.ProductResponse;
import com.grainwarehouse.product.dto.ProductSearchCriteria;
import com.grainwarehouse.product.dto.UpdateProductRequest;
import com.grainwarehouse.product.service.ProductImportDefinition;
import com.grainwarehouse.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductImportDefinition productImport;
    private final ExcelImportService excelImportService;

    // Nhập Excel (BR-XLS): tải mẫu → kiểm tra (không ghi) → xác nhận (ghi tất cả hoặc không gì)
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        return excelImportService.template(productImport);
    }

    @PostMapping("/import/preview")
    public ResponseEntity<ApiResponse<ImportPreview>> previewImport(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(excelImportService.preview(productImport, file)));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ImportResult>> importFile(@RequestParam("file") MultipartFile file) {
        ImportResult result = excelImportService.commit(productImport, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(result, "Đã nhập " + result.importedCount() + " sản phẩm"));
    }

    // Tiêu chí lọc bind từ query string vào record; không gửi = không lọc (request cũ vẫn chạy như trước)
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> list(
            @ModelAttribute ProductSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(productService.list(criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(productService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(productService.create(request), "Tạo sản phẩm thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> update(@PathVariable Long id,
                                                               @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(productService.update(id, request), "Cập nhật sản phẩm thành công"));
    }

    // Soft delete (BR-PRD-08): 204 No Content theo contract-spec
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
