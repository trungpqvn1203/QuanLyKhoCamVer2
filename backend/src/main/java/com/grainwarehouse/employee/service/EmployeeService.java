package com.grainwarehouse.employee.service;

import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.response.PageResponse;
import com.grainwarehouse.common.text.PhoneNumbers;
import com.grainwarehouse.employee.dto.CreateEmployeeRequest;
import com.grainwarehouse.employee.dto.EmployeeResponse;
import com.grainwarehouse.employee.dto.EmployeeSearchCriteria;
import com.grainwarehouse.employee.dto.UpdateEmployeeRequest;
import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.employee.repository.EmployeeRepository;
import com.grainwarehouse.employee.repository.EmployeeSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public PageResponse<EmployeeResponse> list(EmployeeSearchCriteria criteria, Pageable pageable) {
        return PageResponse.from(
                employeeRepository.findAll(EmployeeSpecifications.from(criteria), pageable), EmployeeResponse::from);
    }

    public EmployeeResponse get(Long id) {
        return EmployeeResponse.from(findOrThrow(id));
    }

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest request) {
        Employee employee = new Employee();
        apply(employee, request.name(), request.phone(), request.dailyWage(), request.tonRate());
        // Sau save(), code đã có giá trị nhờ @Generated (BR-EMP-01)
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    /**
     * Đổi đơn giá chỉ ảnh hưởng các tháng CHƯA chốt lương: bảng lương đã chốt giữ bản chụp đơn giá (BR-PAY-02).
     */
    @Transactional
    public EmployeeResponse update(Long id, UpdateEmployeeRequest request) {
        Employee employee = findOrThrow(id);
        apply(employee, request.name(), request.phone(), request.dailyWage(), request.tonRate());
        employee.setActive(request.active());
        return EmployeeResponse.from(employeeRepository.saveAndFlush(employee));
    }

    /** BR-EMP-01: không xóa cứng — chấm công và bảng lương cũ vẫn tham chiếu được nhân viên đã nghỉ. */
    @Transactional
    public void deactivate(Long id) {
        findOrThrow(id).setActive(false);
    }

    private static void apply(Employee employee, String name, String phone, BigDecimal dailyWage,
                              BigDecimal tonRate) {
        // BR-EMP-02: báo vào dailyWage vì đó là ô đầu tiên trong cặp trên form
        if (dailyWage == null && tonRate == null) {
            throw new InvalidRequestException("dailyWage", "Nhập lương ngày hoặc đơn giá tấn (ít nhất một)");
        }
        employee.setName(name.trim());
        employee.setPhone(normalizePhone(phone));
        employee.setDailyWage(dailyWage);
        employee.setTonRate(tonRate);
    }

    /** BR-EMP-03: SĐT tùy chọn; có nhập thì chuẩn hóa như SĐT khách (BR-CUS-06). */
    private static String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String phone = PhoneNumbers.normalize(raw);
        if (!PhoneNumbers.isValid(phone)) {
            throw new InvalidRequestException("phone",
                    "Số điện thoại phải bắt đầu bằng 0 và có 10–11 chữ số (được dùng khoảng trắng, dấu chấm, gạch ngang)");
        }
        return phone;
    }

    private Employee findOrThrow(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên id=" + id));
    }
}
