package com.grainwarehouse.customer.controller;

import com.grainwarehouse.common.importing.ExcelImportService;
import com.grainwarehouse.common.importing.ImportPreview;
import com.grainwarehouse.common.importing.ImportResult;
import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.customer.dto.CreateCustomerRequest;
import com.grainwarehouse.customer.dto.CustomerResponse;
import com.grainwarehouse.customer.dto.CustomerSearchCriteria;
import com.grainwarehouse.customer.dto.UpdateCustomerRequest;
import com.grainwarehouse.customer.service.CustomerImportDefinition;
import com.grainwarehouse.customer.service.CustomerService;
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
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerImportDefinition customerImport;
    private final ExcelImportService excelImportService;

    // Nhập Excel (BR-XLS): tải mẫu → kiểm tra (không ghi) → xác nhận (ghi tất cả hoặc không gì)
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        return excelImportService.template(customerImport);
    }

    @PostMapping("/import/preview")
    public ResponseEntity<ApiResponse<ImportPreview>> previewImport(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(excelImportService.preview(customerImport, file)));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ImportResult>> importFile(@RequestParam("file") MultipartFile file) {
        ImportResult result = excelImportService.commit(customerImport, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(result, "Đã nhập " + result.importedCount() + " khách hàng"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> list(
            @ModelAttribute CustomerSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.list(criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CustomerResponse>> create(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(customerService.create(request), "Tạo khách hàng thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(customerService.update(id, request),
                "Cập nhật khách hàng thành công"));
    }

    // Soft delete (BR-CUS-05): 204 No Content theo contract-spec
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        customerService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
