package com.grainwarehouse.payroll.service;

import com.grainwarehouse.attendance.service.AttendanceMonthLock;
import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.employee.repository.EmployeeRepository;
import com.grainwarehouse.payroll.dto.PayrollItemPaymentsResponse;
import com.grainwarehouse.payroll.dto.PayrollPaymentRequest;
import com.grainwarehouse.payroll.dto.PayrollPaymentResponse;
import com.grainwarehouse.payroll.entity.Payroll;
import com.grainwarehouse.payroll.entity.PayrollItem;
import com.grainwarehouse.payroll.entity.PayrollPayment;
import com.grainwarehouse.payroll.entity.PayrollPaymentMethod;
import com.grainwarehouse.payroll.entity.PayrollPaymentType;
import com.grainwarehouse.payroll.entity.PayrollStatus;
import com.grainwarehouse.payroll.repository.PayrollItemRepository;
import com.grainwarehouse.payroll.repository.PayrollPaymentRepository;
import com.grainwarehouse.payroll.repository.PayrollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Trả lương theo từng dòng lương của bảng lương tháng ĐÃ CHỐT (BR-EPAY-01, 02, 04, 05).
 *
 * <p>Thứ tự khóa cố định ở mọi thao tác thay đổi: khóa THÁNG (cùng khóa với chấm công, chốt và hủy chốt lương) →
 * khóa NHÂN VIÊN (số dư tạm ứng) → khóa DÒNG LƯƠNG. Khóa tháng làm trả lương và hủy chốt xếp hàng nhau: không thể
 * vừa hủy chốt vừa có người trả lương (BR-EPAY-06). Sau khi khóa mới đọc trạng thái bảng lương và kiểm tra số dư.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollPaymentService {

    // Dấu chấm ngăn cách hàng nghìn: 10.000.000 đ
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private final PayrollRepository payrollRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final PayrollPaymentRepository payrollPaymentRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceMonthLock monthLock;

    public PayrollItemPaymentsResponse get(YearMonth month, Long employeeId) {
        Payroll payroll = payrollRepository.findHeader(month.atDay(1), PayrollStatus.FINALIZED)
                .orElseThrow(() -> notFinalized(month));
        PayrollItem item = payrollItemRepository.findItem(payroll.getId(), employeeId)
                .orElseThrow(() -> noItem(month, employeeId));
        return toResponse(month, payroll, item, item.getEmployee());
    }

    /**
     * BR-EPAY-02: trả lương bằng tiền mặt và/hoặc chuyển khoản và/hoặc trừ vào tạm ứng — mỗi phần một dòng, cùng
     * transaction. Tổng không vượt số còn nợ của dòng lương; phần trừ tạm ứng không vượt số dư tạm ứng hiện có.
     */
    @Transactional
    public PayrollItemPaymentsResponse pay(YearMonth month, Long employeeId, PayrollPaymentRequest request) {
        BigDecimal cash = zeroIfNull(request.cashAmount());
        BigDecimal transfer = zeroIfNull(request.transferAmount());
        BigDecimal deduction = zeroIfNull(request.advanceDeduction());
        BigDecimal total = cash.add(transfer).add(deduction);
        if (total.signum() == 0) {
            throw new InvalidRequestException("cashAmount",
                    "Phải nhập số tiền mặt, chuyển khoản hoặc số trừ tạm ứng lớn hơn 0");
        }

        Payroll payroll = lockedPayroll(month);
        Employee employee = employeeRepository.findForUpdate(employeeId).orElseThrow(() -> noItem(month, employeeId));
        PayrollItem item = payrollItemRepository.findForUpdate(payroll.getId(), employeeId)
                .orElseThrow(() -> noItem(month, employeeId));

        if (total.compareTo(item.getOutstandingAmount()) > 0) {
            throw new BusinessException("Số tiền trả " + money(total) + " vượt số lương còn nợ "
                    + money(item.getOutstandingAmount()) + " của " + employee.getName() + " tháng " + label(month));
        }
        if (deduction.compareTo(employee.getAdvanceBalance()) > 0) {
            throw new BusinessException("Số trừ tạm ứng " + money(deduction) + " vượt số dư tạm ứng hiện có "
                    + money(employee.getAdvanceBalance()) + " của " + employee.getName());
        }

        LocalDate paymentDate = request.paymentDate() == null ? today() : request.paymentDate();
        String note = blankToNull(request.note());
        List<PayrollPayment> rows = new ArrayList<>();
        addRow(rows, payroll, employeeId, PayrollPaymentMethod.CASH, cash, paymentDate, note);
        addRow(rows, payroll, employeeId, PayrollPaymentMethod.BANK_TRANSFER, transfer, paymentDate, note);
        addRow(rows, payroll, employeeId, PayrollPaymentMethod.ADVANCE_DEDUCTION, deduction, paymentDate, note);
        payrollPaymentRepository.saveAll(rows);

        item.addPaidAmount(total);
        if (deduction.signum() > 0) {
            employee.addAdvanceBalance(deduction.negate());
        }
        employeeRepository.saveAndFlush(employee);
        payrollItemRepository.saveAndFlush(item);
        return toResponse(month, payroll, item, employee);
    }

    /**
     * BR-EPAY-04: hoàn toàn bộ một khoản trả ghi nhầm bằng dòng REVERSAL trỏ về nó. Mỗi khoản chỉ hoàn một lần.
     * Hoàn khoản TRỪ TẠM ỨNG thì số dư tạm ứng của nhân viên được cộng lại.
     */
    @Transactional
    public PayrollItemPaymentsResponse reversePayment(YearMonth month, Long employeeId, Long paymentId, String reason) {
        Payroll payroll = lockedPayroll(month);
        Employee employee = employeeRepository.findForUpdate(employeeId).orElseThrow(() -> noItem(month, employeeId));
        PayrollItem item = payrollItemRepository.findForUpdate(payroll.getId(), employeeId)
                .orElseThrow(() -> noItem(month, employeeId));

        PayrollPayment original = payrollPaymentRepository
                .findByIdAndPayrollIdAndEmployeeId(paymentId, payroll.getId(), employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khoản trả lương id=" + paymentId
                        + " của " + employee.getName() + " tháng " + label(month)));
        if (original.getType() != PayrollPaymentType.PAYMENT) {
            throw new BusinessException("Chỉ hoàn được khoản trả, không hoàn một dòng hoàn");
        }
        if (payrollPaymentRepository.existsByReversalOfId(original.getId())) {
            throw new BusinessException("Khoản trả này đã được hoàn. Mỗi khoản chỉ được hoàn một lần");
        }

        PayrollPayment reversal = PayrollPayment.reversalOf(original, today(), reason.trim());
        payrollPaymentRepository.save(reversal);
        item.addPaidAmount(reversal.signedAmount());
        if (original.isAdvanceDeduction()) {
            employee.addAdvanceBalance(original.getAmount());
        }
        employeeRepository.saveAndFlush(employee);
        payrollItemRepository.saveAndFlush(item);
        return toResponse(month, payroll, item, employee);
    }

    /**
     * Khóa tháng TRƯỚC khi đọc trạng thái bảng lương: kiểm tra rồi mới khóa thì hủy chốt có thể chen vào giữa.
     * Chỉ bảng lương đã chốt mới có lương để trả; bảng chốt trước module này coi như đã trả đủ (BR-EPAY-05).
     */
    private Payroll lockedPayroll(YearMonth month) {
        monthLock.lock(month);
        Payroll payroll = payrollRepository.findHeader(month.atDay(1), PayrollStatus.FINALIZED)
                .orElseThrow(() -> notFinalized(month));
        if (!payroll.isTracksDebt()) {
            throw new BusinessException("Bảng lương tháng " + label(month)
                    + " chốt trước khi có công nợ nhân viên nên được coi là đã trả đủ, không ghi trả lương");
        }
        return payroll;
    }

    private static void addRow(List<PayrollPayment> rows, Payroll payroll, Long employeeId, PayrollPaymentMethod method,
                               BigDecimal amount, LocalDate date, String note) {
        if (amount.signum() > 0) {
            rows.add(PayrollPayment.payment(payroll.getId(), employeeId, method, amount, date, note));
        }
    }

    private PayrollItemPaymentsResponse toResponse(YearMonth month, Payroll payroll, PayrollItem item, Employee employee) {
        List<PayrollPayment> payments = payrollPaymentRepository.findByItem(payroll.getId(), employee.getId());
        Set<Long> reversedIds = payments.stream()
                .filter(payment -> payment.getReversalOf() != null)
                .map(payment -> payment.getReversalOf().getId())
                .collect(Collectors.toSet());
        return new PayrollItemPaymentsResponse(
                month,
                employee.getId(),
                employee.getCode(),
                employee.getName(),
                employee.isActive(),
                item.getTotalAmount(),
                item.getPaidAmount(),
                payroll.isTracksDebt() ? item.getOutstandingAmount() : BigDecimal.ZERO,
                payroll.isTracksDebt(),
                employee.getAdvanceBalance(),
                payments.stream().map(payment -> PayrollPaymentResponse.from(payment, reversedIds)).toList());
    }

    private static BusinessException notFinalized(YearMonth month) {
        return new BusinessException("Tháng " + label(month) + " chưa chốt lương, chưa có lương để trả");
    }

    private static ResourceNotFoundException noItem(YearMonth month, Long employeeId) {
        return new ResourceNotFoundException(
                "Không có lương tháng " + label(month) + " của nhân viên id=" + employeeId);
    }

    private static String label(YearMonth month) {
        return String.format("%02d/%d", month.getMonthValue(), month.getYear());
    }

    private static LocalDate today() {
        return LocalDate.now(CommonSpecifications.BUSINESS_ZONE);
    }

    // 10000000 → "10.000.000 đ" (tiền luôn là số đồng nguyên)
    private static String money(BigDecimal amount) {
        return String.format(VIETNAMESE, "%,d đ", amount.toBigInteger());
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
