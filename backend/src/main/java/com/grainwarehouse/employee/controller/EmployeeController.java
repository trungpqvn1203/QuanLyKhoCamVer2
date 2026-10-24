package com.grainwarehouse.employee.controller;

import com.grainwarehouse.common.importing.ExcelImportService;
import com.grainwarehouse.common.importing.ImportPreview;
import com.grainwarehouse.common.importing.ImportResult;
import com.grainwarehouse.common.response.ApiResponse;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.employee.dto.CreateEmployeeRequest;
import com.grainwarehouse.employee.dto.EmployeeResponse;
import com.grainwarehouse.employee.dto.EmployeeSearchCriteria;
import com.grainwarehouse.employee.dto.UpdateEmployeeRequest;
import com.grainwarehouse.employee.service.EmployeeImportDefinition;
import com.grainwarehouse.employee.service.EmployeeService;
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
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeImportDefinition employeeImport;
    private final ExcelImportService excelImportService;

    // Nhập Excel (BR-XLS): tải mẫu → kiểm tra (không ghi) → xác nhận (ghi tất cả hoặc không gì)
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        return excelImportService.template(employeeImport);
    }

    @PostMapping("/import/preview")
    public ResponseEntity<ApiResponse<ImportPreview>> previewImport(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(excelImportService.preview(employeeImport, file)));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ImportResult>> importFile(@RequestParam("file") MultipartFile file) {
        ImportResult result = excelImportService.commit(employeeImport, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(result, "Đã nhập " + result.importedCount() + " nhân viên"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<EmployeeResponse>>> list(
            @ModelAttribute EmployeeSearchCriteria criteria,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(employeeService.list(criteria, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(employeeService.get(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeResponse>> create(@Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(employeeService.create(request), "Tạo nhân viên thành công"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EmployeeResponse>> update(@PathVariable Long id,
                                                                @Valid @RequestBody UpdateEmployeeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(employeeService.update(id, request),
                "Cập nhật nhân viên thành công"));
    }

    // Soft delete (BR-EMP-01): 204 No Content theo contract-spec
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        employeeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
