package com.grainwarehouse.employee.service;

import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.exception.InvalidRequestException;
import com.grainwarehouse.common.exception.ResourceNotFoundException;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.employee.dto.EmployeeAdvanceLedgerResponse;
import com.grainwarehouse.employee.dto.EmployeeAdvanceResponse;
import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.employee.entity.EmployeeAdvance;
import com.grainwarehouse.employee.entity.EmployeeAdvanceType;
import com.grainwarehouse.employee.repository.EmployeeAdvanceRepository;
import com.grainwarehouse.employee.repository.EmployeeRepository;
import com.grainwarehouse.sales.dto.RecordPaymentRequest;
import com.grainwarehouse.sales.entity.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tạm ứng cho nhân viên (BR-EPAY-03, 04). Số dư tạm ứng nằm ở Employee.advanceBalance (snapshot, không bao giờ âm);
 * nhật ký employee_advance bất biến. Mọi thao tác đổi số dư lấy nhân viên bằng findForUpdate TRƯỚC khi kiểm tra
 * (khóa trước, kiểm tra sau) — cùng nguyên tắc với khóa phiếu kho.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeAdvanceService {

    // Dấu chấm ngăn cách hàng nghìn: 10.000.000 đ
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private final EmployeeRepository employeeRepository;
    private final EmployeeAdvanceRepository employeeAdvanceRepository;

    public EmployeeAdvanceLedgerResponse get(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> notFound(employeeId));
        return toResponse(employee);
    }

    /**
     * BR-EPAY-03: ứng tiền mặt và/hoặc chuyển khoản — mỗi hình thức một dòng, cùng transaction. Chỉ ứng cho nhân viên
     * đang làm việc. Ngày ứng chọn tự do (như thu tiền khách), không gửi = hôm nay.
     */
    @Transactional
    public EmployeeAdvanceLedgerResponse record(Long employeeId, RecordPaymentRequest request) {
        BigDecimal cash = zeroIfNull(request.cashAmount());
        BigDecimal transfer = zeroIfNull(request.transferAmount());
        BigDecimal total = cash.add(transfer);
        if (total.signum() == 0) {
            throw new InvalidRequestException("cashAmount", "Phải nhập số tiền mặt hoặc chuyển khoản lớn hơn 0");
        }

        Employee employee = employeeRepository.findForUpdate(employeeId).orElseThrow(() -> notFound(employeeId));
        if (!employee.isActive()) {
            throw new BusinessException("Nhân viên '" + employee.getName() + "' (" + employee.getCode()
                    + ") đã nghỉ việc, không ứng tiền được");
        }
        LocalDate advanceDate = request.paymentDate() == null ? today() : request.paymentDate();
        String note = blankToNull(request.note());

        List<EmployeeAdvance> rows = new ArrayList<>();
        if (cash.signum() > 0) {
            rows.add(EmployeeAdvance.advance(employee, PaymentMethod.CASH, cash, advanceDate, note));
        }
        if (transfer.signum() > 0) {
            rows.add(EmployeeAdvance.advance(employee, PaymentMethod.BANK_TRANSFER, transfer, advanceDate, note));
        }
        employeeAdvanceRepository.saveAll(rows);
        employee.addAdvanceBalance(total);
        employeeRepository.saveAndFlush(employee);
        return toResponse(employee);
    }

    /**
     * BR-EPAY-04: hoàn toàn bộ một khoản ứng ghi nhầm bằng dòng REVERSAL trỏ về nó. Mỗi khoản chỉ hoàn một lần.
     * Chỉ hoàn được khi số dư tạm ứng còn đủ: nếu một phần đã trừ vào lương thì phải hoàn khoản trừ lương trước,
     * nếu không số dư sẽ âm (nhân viên "ứng âm" = công ty nợ nhân viên mà không có chứng từ nào giải thích).
     */
    @Transactional
    public EmployeeAdvanceLedgerResponse reverse(Long employeeId, Long advanceId, String reason) {
        Employee employee = employeeRepository.findForUpdate(employeeId).orElseThrow(() -> notFound(employeeId));
        EmployeeAdvance original = employeeAdvanceRepository.findByIdAndEmployeeId(advanceId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy khoản ứng id=" + advanceId + " của nhân viên " + employee.getCode()));
        if (original.getType() != EmployeeAdvanceType.ADVANCE) {
            throw new BusinessException("Chỉ hoàn được khoản ứng, không hoàn một dòng hoàn");
        }
        if (employeeAdvanceRepository.existsByReversalOfId(original.getId())) {
            throw new BusinessException("Khoản ứng này đã được hoàn. Mỗi khoản chỉ được hoàn một lần");
        }
        if (employee.getAdvanceBalance().compareTo(original.getAmount()) < 0) {
            throw new BusinessException("Số dư tạm ứng của nhân viên chỉ còn " + money(employee.getAdvanceBalance())
                    + ", không đủ để hoàn khoản ứng " + money(original.getAmount())
                    + " (phần đã trừ vào lương). Hãy hoàn các khoản trừ tạm ứng trong bảng lương trước");
        }

        EmployeeAdvance reversal = EmployeeAdvance.reversalOf(original, today(), reason.trim());
        employeeAdvanceRepository.save(reversal);
        employee.addAdvanceBalance(reversal.signedAmount());
        employeeRepository.saveAndFlush(employee);
        return toResponse(employee);
    }

    private EmployeeAdvanceLedgerResponse toResponse(Employee employee) {
        List<EmployeeAdvance> advances = employeeAdvanceRepository.findByEmployeeId(employee.getId());
        Set<Long> reversedIds = advances.stream()
                .filter(advance -> advance.getReversalOf() != null)
                .map(advance -> advance.getReversalOf().getId())
                .collect(Collectors.toSet());
        return new EmployeeAdvanceLedgerResponse(employee.getId(), employee.getCode(), employee.getName(),
                employee.isActive(), employee.getAdvanceBalance(),
                advances.stream().map(advance -> EmployeeAdvanceResponse.from(advance, reversedIds)).toList());
    }

    private static ResourceNotFoundException notFound(Long employeeId) {
        return new ResourceNotFoundException("Không tìm thấy nhân viên id=" + employeeId);
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
