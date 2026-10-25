package com.grainwarehouse.payroll.service;

import com.grainwarehouse.attendance.entity.Attendance;
import com.grainwarehouse.attendance.repository.AttendanceRepository;
import com.grainwarehouse.attendance.service.AttendanceMonthLock;
import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.payroll.dto.PayrollSheetResponse;
import com.grainwarehouse.payroll.entity.Payroll;
import com.grainwarehouse.payroll.entity.PayrollItem;
import com.grainwarehouse.payroll.entity.PayrollStatus;
import com.grainwarehouse.payroll.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollService {

    private final PayrollRepository payrollRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceMonthLock monthLock;

    /** BR-PAY-02: đã chốt → bản chụp; chưa chốt → tạm tính từ chấm công với đơn giá hiện tại. */
    public PayrollSheetResponse sheet(YearMonth month) {
        List<PayrollSheetResponse.HistoryEntry> history = payrollRepository
                .findByPeriodAndStatusOrderByIdDesc(month.atDay(1), PayrollStatus.CANCELLED).stream()
                .map(PayrollService::toHistory)
                .toList();
        return payrollRepository.findByPeriodAndStatus(month.atDay(1), PayrollStatus.FINALIZED)
                .map(payroll -> fromSnapshot(payroll, history))
                .orElseGet(() -> preview(month, history));
    }

    /**
     * Chốt lương (BR-PAY-02..03). Khóa tháng TRƯỚC khi đọc chấm công: trong lúc đang tính, không ai chấm thêm
     * được vào tháng này (AttendanceService cũng lấy cùng khóa) → bản chụp khớp đúng bảng công.
     */
    @Transactional
    public PayrollSheetResponse finalizeMonth(YearMonth month) {
        if (month.isAfter(YearMonth.now(CommonSpecifications.BUSINESS_ZONE))) {
            throw new InvalidRequestException("month", "Không chốt lương cho tháng ở tương lai");
        }
        monthLock.lock(month);
        if (monthLock.isPayrollFinalized(month)) {
            throw new BusinessException("Tháng " + label(month) + " đã chốt lương. Hủy chốt trước nếu cần tính lại");
        }
        List<Totals> totals = totalsByEmployee(month);
        if (totals.isEmpty()) {
            throw new BusinessException("Tháng " + label(month) + " chưa có chấm công nào, không có gì để chốt");
        }

        Payroll payroll = Payroll.create(month, Instant.now());
        for (Totals t : totals) {
            Employee e = t.employee();
            payroll.addItem(e, t.workUnits(), t.unloadTons(), t.deliveryTons(),
                    PayCalculator.dayAmount(t.workUnits(), e.getDailyWage()),
                    PayCalculator.tonAmount(t.unloadTons(), e.getUnloadTonRate()),
                    PayCalculator.tonAmount(t.deliveryTons(), e.getDeliveryTonRate()));
        }
        // Hai request chốt cùng lúc đã bị khóa tháng xếp hàng; uk_payroll_finalized_period là chốt chặn cuối
        payrollRepository.saveAndFlush(payroll);
        return sheet(month);
    }

    /** BR-PAY-03: hủy chốt — giữ bản cũ (CANCELLED) làm lịch sử, tháng mở lại để sửa chấm công. */
    @Transactional
    public PayrollSheetResponse cancel(YearMonth month, String reason) {
        monthLock.lock(month);
        Payroll payroll = payrollRepository.findByPeriodAndStatus(month.atDay(1), PayrollStatus.FINALIZED)
                .orElseThrow(() -> new BusinessException("Tháng " + label(month) + " chưa chốt lương, không có gì để hủy"));
        payroll.cancel(reason.trim(), Instant.now());
        payrollRepository.saveAndFlush(payroll);
        return sheet(month);
    }

    private PayrollSheetResponse preview(YearMonth month, List<PayrollSheetResponse.HistoryEntry> history) {
        List<PayrollSheetResponse.Line> lines = totalsByEmployee(month).stream()
                .map(t -> {
                    Employee e = t.employee();
                    BigDecimal day = PayCalculator.dayAmount(t.workUnits(), e.getDailyWage());
                    BigDecimal unload = PayCalculator.tonAmount(t.unloadTons(), e.getUnloadTonRate());
                    BigDecimal delivery = PayCalculator.tonAmount(t.deliveryTons(), e.getDeliveryTonRate());
                    return new PayrollSheetResponse.Line(e.getId(), e.getCode(), e.getName(), e.isActive(),
                            t.workUnits(), t.unloadTons(), t.deliveryTons(), e.getDailyWage(),
                            e.getUnloadTonRate(), e.getDeliveryTonRate(), day, unload, delivery,
                            day.add(unload).add(delivery));
                })
                .toList();
        BigDecimal total = lines.stream().map(PayrollSheetResponse.Line::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayrollSheetResponse(month, false, null, null, total, lines, history);
    }

    private static PayrollSheetResponse fromSnapshot(Payroll payroll, List<PayrollSheetResponse.HistoryEntry> history) {
        List<PayrollSheetResponse.Line> lines = payroll.getItems().stream()
                .sorted(Comparator.comparing((PayrollItem item) -> item.getEmployee().getCode()))
                .map(item -> new PayrollSheetResponse.Line(
                        item.getEmployee().getId(),
                        item.getEmployee().getCode(),
                        item.getEmployee().getName(),
                        item.getEmployee().isActive(),
                        item.getWorkUnits(),
                        item.getUnloadTons(),
                        item.getDeliveryTons(),
                        item.getDailyWage(),
                        item.getUnloadTonRate(),
                        item.getDeliveryTonRate(),
                        item.getDayAmount(),
                        item.getUnloadAmount(),
                        item.getDeliveryAmount(),
                        item.getTotalAmount()))
                .toList();
        return new PayrollSheetResponse(payroll.month(), true, payroll.getId(), payroll.getFinalizedAt(),
                payroll.getTotalAmount(), lines, history);
    }

    private static PayrollSheetResponse.HistoryEntry toHistory(Payroll payroll) {
        return new PayrollSheetResponse.HistoryEntry(payroll.getId(), payroll.getStatus(), payroll.getTotalAmount(),
                payroll.getFinalizedAt(), payroll.getCancelledAt(), payroll.getCancelReason());
    }

    /** Tổng công + tổng tấn mỗi nhân viên có chấm công trong tháng (kể cả NV đã nghỉ), sắp theo mã. */
    private List<Totals> totalsByEmployee(YearMonth month) {
        Map<Long, Totals> byEmployee = new LinkedHashMap<>();
        for (Attendance a : attendanceRepository.findInRange(month.atDay(1), month.atEndOfMonth())) {
            byEmployee.merge(a.getEmployee().getId(),
                    new Totals(a.getEmployee(), a.getDayStatus().workUnits(), a.getUnloadTons(), a.getDeliveryTons()),
                    Totals::plus);
        }
        return byEmployee.values().stream().sorted(Comparator.comparing(t -> t.employee().getCode())).toList();
    }

    private static String label(YearMonth month) {
        return String.format("%02d/%d", month.getMonthValue(), month.getYear());
    }

    private record Totals(Employee employee, BigDecimal workUnits, BigDecimal unloadTons, BigDecimal deliveryTons) {
        Totals plus(Totals other) {
            return new Totals(employee, workUnits.add(other.workUnits), unloadTons.add(other.unloadTons),
                    deliveryTons.add(other.deliveryTons));
        }
    }
}
