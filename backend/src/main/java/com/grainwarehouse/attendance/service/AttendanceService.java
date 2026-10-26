package com.grainwarehouse.attendance.service;

import com.grainwarehouse.attendance.dto.AttendanceDay;
import com.grainwarehouse.attendance.dto.AttendanceRequest;
import com.grainwarehouse.attendance.dto.MonthlyAttendanceResponse;
import com.grainwarehouse.attendance.entity.Attendance;
import com.grainwarehouse.attendance.entity.DayStatus;
import com.grainwarehouse.attendance.repository.AttendanceRepository;
import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceMonthLock monthLock;

    /**
     * Dòng của bảng công = nhân viên đang làm + nhân viên đã nghỉ nhưng CÓ chấm công trong tháng
     * (xem lại tháng cũ vẫn thấy đủ người). 2 câu SQL cho cả tháng, không phụ thuộc số nhân viên.
     */
    public MonthlyAttendanceResponse monthly(YearMonth month) {
        List<Attendance> records = attendanceRepository.findInRange(month.atDay(1), month.atEndOfMonth());

        Map<Long, Employee> employees = new LinkedHashMap<>();
        employeeRepository.findByActiveTrue(Sort.by("code")).forEach(e -> employees.put(e.getId(), e));
        records.forEach(a -> employees.putIfAbsent(a.getEmployee().getId(), a.getEmployee()));

        Map<Long, List<AttendanceDay>> daysByEmployee = new LinkedHashMap<>();
        for (Attendance record : records) {
            daysByEmployee.computeIfAbsent(record.getEmployee().getId(), id -> new ArrayList<>())
                    .add(AttendanceDay.from(record));
        }

        List<MonthlyAttendanceResponse.Row> rows = employees.values().stream()
                .sorted(Comparator.comparing(Employee::getCode))
                .map(employee -> toRow(employee, daysByEmployee.getOrDefault(employee.getId(), List.of())))
                .toList();
        return new MonthlyAttendanceResponse(month, month.lengthOfMonth(), today(),
                monthLock.isPayrollFinalized(month), rows);
    }

    /** Ghi (tạo hoặc ghi đè) một ô. BR-ATT-01..02. */
    @Transactional
    public AttendanceDay record(Long employeeId, LocalDate date, AttendanceRequest request) {
        // Khóa tháng TRƯỚC khi đọc ô: hai người cùng ghi một ô mới sẽ xếp hàng, người sau thấy ô người trước vừa tạo;
        // và chốt lương (cùng khóa) không thể chen vào giữa lúc kiểm tra "chưa chốt" và lúc ghi
        lockMonthForChange(YearMonth.from(date));
        Employee employee = writableEmployee(employeeId, date);
        if (request.status() == DayStatus.ABSENT) {
            if (request.effectiveUnloadTons().signum() > 0) {
                throw new InvalidRequestException("unloadTons", "Ngày nghỉ thì không có tấn bốc xuống");
            }
            if (request.effectiveDeliveryTons().signum() > 0) {
                throw new InvalidRequestException("deliveryTons", "Ngày nghỉ thì không có tấn giao hàng");
            }
        }

        Attendance attendance = attendanceRepository.findByEmployeeIdAndWorkDate(employeeId, date)
                .orElseGet(() -> new Attendance(employee, date));
        attendance.record(request.status(), request.effectiveUnloadTons(), request.effectiveDeliveryTons(),
                blankToNull(request.note()));
        return AttendanceDay.from(attendanceRepository.saveAndFlush(attendance));
    }

    /** Xóa ô ghi nhầm — ngày đó trở về "chưa chấm". */
    @Transactional
    public void clear(Long employeeId, LocalDate date) {
        lockMonthForChange(YearMonth.from(date));
        writableEmployee(employeeId, date);
        Attendance attendance = attendanceRepository.findByEmployeeIdAndWorkDate(employeeId, date)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Nhân viên id=" + employeeId + " chưa được chấm công ngày " + date));
        attendanceRepository.delete(attendance);
    }

    /** BR-PAY-03: khóa tháng rồi mới kiểm tra đã chốt — kiểm tra trước khi khóa thì kết quả có thể cũ ngay lập tức. */
    private void lockMonthForChange(YearMonth month) {
        monthLock.lock(month);
        if (monthLock.isPayrollFinalized(month)) {
            throw new BusinessException(String.format("Tháng %02d/%d đã chốt lương, không sửa được chấm công."
                    + " Hủy chốt bảng lương tháng này trước nếu cần sửa", month.getMonthValue(), month.getYear()));
        }
    }

    /** BR-ATT-02: không chấm ngày tương lai (giờ VN), không chấm cho nhân viên đã nghỉ. */
    private Employee writableEmployee(Long employeeId, LocalDate date) {
        if (date.isAfter(today())) {
            throw new InvalidRequestException("date", "Không chấm công cho ngày ở tương lai");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên id=" + employeeId));
        if (!employee.isActive()) {
            throw new BusinessException("Nhân viên '" + employee.getName() + "' (" + employee.getCode()
                    + ") đã nghỉ việc, không chấm công được. Cho làm lại trước nếu cần sửa công");
        }
        return employee;
    }

    private static MonthlyAttendanceResponse.Row toRow(Employee employee, List<AttendanceDay> days) {
        BigDecimal units = days.stream().map(d -> d.status().workUnits()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unloadTons = days.stream().map(AttendanceDay::unloadTons).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveryTons = days.stream().map(AttendanceDay::deliveryTons).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MonthlyAttendanceResponse.Row(
                employee.getId(),
                employee.getCode(),
                employee.getName(),
                employee.isActive(),
                employee.getDailyWage() != null,
                employee.getUnloadTonRate() != null,
                employee.getDeliveryTonRate() != null,
                units,
                unloadTons,
                deliveryTons,
                days);
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
