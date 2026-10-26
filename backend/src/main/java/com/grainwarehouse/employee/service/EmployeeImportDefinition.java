package com.grainwarehouse.employee.service;

import com.grainwarehouse.common.importing.ImportColumn;
import com.grainwarehouse.common.importing.ImportDefinition;
import com.grainwarehouse.common.importing.ImportRow;
import com.grainwarehouse.common.text.PhoneNumbers;
import com.grainwarehouse.employee.dto.CreateEmployeeRequest;
import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Mẫu nhập nhân viên (BR-XLS). Nhân viên không có khóa duy nhất (BR-EMP-03: SĐT không bắt buộc, tên được trùng)
 * nên KHÔNG so trùng với DB — giống form nhập tay. Chỉ chặn hai dòng cùng tên + SĐT trong một file
 * (thường do dán lặp) — BR-XLS-A01, giả định chờ xác nhận.
 */
@Component
@RequiredArgsConstructor
public class EmployeeImportDefinition implements ImportDefinition<CreateEmployeeRequest, Employee> {

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;

    @Override
    public String templateId() {
        // v2 (2026-10-26): "Đơn giá tấn" tách thành đơn giá tấn bốc xuống và tấn giao hàng — mẫu v1 bị từ chối, không đọc lệch cột
        return "EMPLOYEE_v2";
    }

    @Override
    public String entityLabel() {
        return "nhân viên";
    }

    @Override
    public String fileName() {
        return "mau-nhap-nhan-vien.xlsx";
    }

    @Override
    public List<ImportColumn> columns() {
        return List.of(
                ImportColumn.required("name", "Tên nhân viên", "Tối đa 200 ký tự"),
                ImportColumn.optional("phone", "Số điện thoại",
                        "Bắt đầu bằng 0, 10–11 chữ số; được dùng khoảng trắng, dấu chấm, gạch ngang").asText(),
                ImportColumn.optional("dailyWage", "Lương ngày (đồng)",
                        "Số ≥ 0, tối đa 2 chữ số thập phân. Phải có ít nhất một trong ba loại lương"),
                ImportColumn.optional("unloadTonRate", "Đơn giá tấn bốc xuống (đồng/tấn)",
                        "Số ≥ 0, tối đa 2 chữ số thập phân. Phải có ít nhất một trong ba loại lương"),
                ImportColumn.optional("deliveryTonRate", "Đơn giá tấn giao hàng (đồng/tấn)",
                        "Số ≥ 0, tối đa 2 chữ số thập phân. Phải có ít nhất một trong ba loại lương"));
    }

    @Override
    public List<String> example() {
        return List.of("Nguyễn Văn Bốc", "0912 345 678", "300000", "25000", "30000");
    }

    @Override
    public CreateEmployeeRequest parse(ImportRow row) {
        return new CreateEmployeeRequest(row.text("name"), row.text("phone"), row.decimal("dailyWage"),
                row.decimal("unloadTonRate"), row.decimal("deliveryTonRate"));
    }

    @Override
    public Map<String, String> duplicateKeys(CreateEmployeeRequest request) {
        String phone = request.phone() == null ? "" : PhoneNumbers.normalize(request.phone());
        return Map.of("name", ImportRow.normalizeKey(request.name()) + "|" + phone);
    }

    @Override
    public Employee prepare(CreateEmployeeRequest request) {
        return employeeService.prepareNew(request);
    }

    @Override
    public void saveAll(List<Employee> employees) {
        employeeRepository.saveAll(employees);
    }
}
