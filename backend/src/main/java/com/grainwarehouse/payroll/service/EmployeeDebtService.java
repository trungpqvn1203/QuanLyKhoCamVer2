package com.grainwarehouse.payroll.service;

import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.employee.repository.EmployeeRepository;
import com.grainwarehouse.payroll.dto.EmployeeDebtResponse;
import com.grainwarehouse.payroll.entity.PayrollItem;
import com.grainwarehouse.payroll.repository.PayrollItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Công nợ nhân viên hai chiều (BR-EPAY-01, 03): lương công ty còn nợ + tạm ứng nhân viên còn nợ. Tính từ
 * paid_amount của dòng lương và advance_balance của nhân viên, không có bảng số dư riêng. Chỉ liệt kê nhân viên có
 * ít nhất một món nợ; lương chưa trả nhiều nhất lên đầu.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeDebtService {

    private final PayrollItemRepository payrollItemRepository;
    private final EmployeeRepository employeeRepository;

    public List<EmployeeDebtResponse> list(Long employeeId) {
        Map<Long, Debt> debts = new LinkedHashMap<>();
        for (PayrollItem item : payrollItemRepository.findUnpaid()) {
            Employee employee = item.getEmployee();
            if (employeeId != null && !employee.getId().equals(employeeId)) {
                continue;
            }
            debts.computeIfAbsent(employee.getId(), id -> new Debt(employee)).add(item);
        }
        for (Employee employee : employeeRepository.findByAdvanceBalanceGreaterThan(BigDecimal.ZERO)) {
            if (employeeId == null || employee.getId().equals(employeeId)) {
                debts.computeIfAbsent(employee.getId(), id -> new Debt(employee));
            }
        }
        return debts.values().stream()
                .map(Debt::toResponse)
                .sorted(Comparator.comparing(EmployeeDebtResponse::unpaidSalaryAmount).reversed()
                        .thenComparing(EmployeeDebtResponse::employeeCode))
                .toList();
    }

    private static final class Debt {
        private final Employee employee;
        private final List<EmployeeDebtResponse.UnpaidMonth> months = new ArrayList<>();
        private BigDecimal unpaid = BigDecimal.ZERO;

        Debt(Employee employee) {
            this.employee = employee;
        }

        void add(PayrollItem item) {
            months.add(new EmployeeDebtResponse.UnpaidMonth(YearMonth.from(item.getPayroll().getPeriod()),
                    item.getTotalAmount(), item.getPaidAmount(), item.getOutstandingAmount()));
            unpaid = unpaid.add(item.getOutstandingAmount());
        }

        EmployeeDebtResponse toResponse() {
            return new EmployeeDebtResponse(employee.getId(), employee.getCode(), employee.getName(),
                    employee.isActive(), unpaid, employee.getAdvanceBalance(),
                    unpaid.subtract(employee.getAdvanceBalance()), months);
        }
    }
}
