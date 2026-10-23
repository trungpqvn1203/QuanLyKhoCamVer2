package com.grainwarehouse.supplier.controller;

import com.grainwarehouse.common.importing.ExcelImportService;
import com.grainwarehouse.common.importing.ImportPreview;
import com.grainwarehouse.common.importing.ImportResult;
import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.supplier.dto.CreateSupplierRequest;
import com.grainwarehouse.supplier.dto.LinkProductRequest;
import com.grainwarehouse.supplier.dto.SupplierProductResponse;
import com.grainwarehouse.supplier.dto.SupplierResponse;
import com.grainwarehouse.supplier.dto.SupplierSearchCriteria;
import com.grainwarehouse.supplier.dto.UpdateSupplierRequest;
import com.grainwarehouse.supplier.service.SupplierImportDefinition;
import com.grainwarehouse.supplier.service.SupplierService;
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
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;
    private final SupplierImportDefinition supplierImport;
    private final ExcelImportService excelImportService;

    // Nhập Excel (BR-XLS): tải mẫu → kiểm tra (không ghi) → xác nhận (ghi tất cả hoặc không gì)
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        return excelImportService.template(supplierImport);
    }

    @PostMapping("/import/preview")
    public ResponseEntity<ApiResponse<ImportPreview>> previewImport(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(excelImportService.preview(supplierImport, file)));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ImportResult>> importFile(@RequestParam("file") MultipartFile file) {
        ImportResult result = excelImportService.commit(supplierImport, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(result, "Đã nhập " + result.importedCount() + " nhà cung cấp"));
    }

    // Tiêu chí lọc bind từ query string vào record; không gửi = không lọc (request cũ vẫn chạy như trước)
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SupplierResponse>>> list(
            @ModelAttribute SupplierSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.list(criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SupplierResponse>> create(@Valid @RequestBody CreateSupplierRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(supplierService.create(request), "Tạo nhà cung cấp thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SupplierResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateSupplierRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.update(id, request),
                "Cập nhật nhà cung cấp thành công"));
    }

    // Soft delete (BR-SUP-04): 204 No Content theo contract-spec
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        supplierService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // Sắp xếp mặc định theo mã SP; sort nhận thuộc tính của ProductSupplier (ví dụ product.name)
    @GetMapping("/{id}/products")
    public ResponseEntity<ApiResponse<PageResponse<SupplierProductResponse>>> listProducts(
            @PathVariable Long id,
            @PageableDefault(size = 20, sort = "product.code") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(supplierService.listProducts(id, pageable)));
    }

    // Gán mới hoặc bật lại liên kết đã gỡ — cả hai trả 201 (BR-SUP-05)
    @PostMapping("/{id}/products")
    public ResponseEntity<ApiResponse<SupplierProductResponse>> linkProduct(
            @PathVariable Long id, @Valid @RequestBody LinkProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(supplierService.linkProduct(id, request), "Gán sản phẩm thành công"));
    }

    // Gỡ liên kết = active=false (BR-SUP-05)
    @DeleteMapping("/{id}/products/{productId}")
    public ResponseEntity<Void> unlinkProduct(@PathVariable Long id, @PathVariable Long productId) {
        supplierService.unlinkProduct(id, productId);
        return ResponseEntity.noContent().build();
    }
}
