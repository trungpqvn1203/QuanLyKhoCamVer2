package com.grainwarehouse.payroll;

import com.grainwarehouse.attendance.dto.AttendanceRequest;
import com.grainwarehouse.attendance.entity.DayStatus;
import com.grainwarehouse.attendance.service.AttendanceService;
import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.employee.service.EmployeeAdvanceService;
import com.grainwarehouse.payroll.dto.PayrollPaymentRequest;
import com.grainwarehouse.payroll.service.PayrollPaymentService;
import com.grainwarehouse.payroll.service.PayrollService;
import com.grainwarehouse.sales.dto.RecordPaymentRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hai quy tắc "không âm" của công nợ nhân viên khi nhiều người bấm cùng lúc (cùng tinh thần "không tồn kho âm"):
 * - số dư tạm ứng (BR-EPAY-03): nhiều lần trừ lương cùng rút từ MỘT số dư;
 * - số lương còn nợ của một dòng (BR-EPAY-02): nhiều lần trả cùng một dòng lương.
 * Mỗi lần trừ/trả hoặc thành công, hoặc bị từ chối bằng BusinessException — không được có kết quả "ai cũng thấy đủ".
 * Không @Transactional (cần commit thật) → tự dọn ở @AfterEach; bảng nhật ký bất biến nên tạm tắt trigger để xóa.
 */
@SpringBootTest
@ActiveProfiles("test")
class EmployeeDebtConcurrencyTest {

    private static final int THREADS = 5;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PayrollPaymentService payrollPaymentService;

    @Autowired
    private EmployeeAdvanceService employeeAdvanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long employeeId;

    @BeforeEach
    void setUp() {
        employeeId = jdbcTemplate.queryForObject(
                "INSERT INTO employee (name, daily_wage) VALUES ('NV nợ đồng thời', 300000) RETURNING id", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("ALTER TABLE payroll_payment DISABLE TRIGGER trg_payroll_payment_immutable");
        jdbcTemplate.update("DELETE FROM payroll_payment WHERE employee_id = ?", employeeId);
        jdbcTemplate.execute("ALTER TABLE payroll_payment ENABLE TRIGGER trg_payroll_payment_immutable");
        jdbcTemplate.execute("ALTER TABLE employee_advance DISABLE TRIGGER trg_employee_advance_immutable");
        jdbcTemplate.update("DELETE FROM employee_advance WHERE employee_id = ?", employeeId);
        jdbcTemplate.execute("ALTER TABLE employee_advance ENABLE TRIGGER trg_employee_advance_immutable");
        jdbcTemplate.update("DELETE FROM payroll_item WHERE employee_id = ?", employeeId);
        jdbcTemplate.update("DELETE FROM payroll WHERE id NOT IN (SELECT payroll_id FROM payroll_item)");
        jdbcTemplate.update("DELETE FROM attendance WHERE employee_id = ?", employeeId);
        jdbcTemplate.update("DELETE FROM employee WHERE id = ?", employeeId);
    }

    @Test
    @DisplayName("BR-EPAY-03: số dư tạm ứng 100.000, 5 lần trừ 60.000 ở 5 tháng khác nhau cùng lúc - đúng 1 lần thành công, số dư không âm")
    void advanceBalanceShouldNeverGoNegative() throws Exception {
        List<YearMonth> months = finalizedMonths(THREADS);
        employeeAdvanceService.record(employeeId, new RecordPaymentRequest(null, new BigDecimal("100000"), null, null));

        int succeeded = runConcurrently(months,
                month -> payrollPaymentService.pay(month, employeeId,
                        new PayrollPaymentRequest(null, null, null, new BigDecimal("60000"), null)));

        assertThat(succeeded).isEqualTo(1);
        assertThat(advanceBalance()).isEqualByComparingTo("40000");
        assertThat(sum("SELECT coalesce(sum(amount), 0) FROM payroll_payment WHERE employee_id = ?"
                + " AND method = 'ADVANCE_DEDUCTION'")).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("BR-EPAY-02: dòng lương 300.000, 5 lần trả 100.000 cùng lúc - đúng 3 lần thành công, đã trả không vượt tổng lương")
    void paidShouldNeverExceedTotal() throws Exception {
        YearMonth month = finalizedMonths(1).getFirst();

        int succeeded = runConcurrently(java.util.Collections.nCopies(THREADS, month),
                m -> payrollPaymentService.pay(m, employeeId,
                        new PayrollPaymentRequest(null, new BigDecimal("100000"), null, null, null)));

        assertThat(succeeded).isEqualTo(3);
        assertThat(sum("SELECT paid_amount FROM payroll_item WHERE employee_id = ?")).isEqualByComparingTo("300000");
        assertThat(sum("SELECT count(*) FROM payroll_payment WHERE employee_id = ?")).isEqualByComparingTo("3");
    }

    /** Chạy mỗi phần tử trên một luồng, thả cùng lúc; trả về số lần thành công. Lỗi ngoài BusinessException là lỗi test. */
    private int runConcurrently(List<YearMonth> months, PayAction action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(months.size());
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (YearMonth month : months) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return action.run(month);
                }));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(BusinessException.class);
                }
            }
            return succeeded;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Mỗi tháng một công × 300.000 = 300.000 lương, đã chốt. Lùi dần từ tháng trước. */
    private List<YearMonth> finalizedMonths(int count) {
        List<YearMonth> months = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            YearMonth month = YearMonth.now(CommonSpecifications.BUSINESS_ZONE).minusMonths(i);
            attendanceService.record(employeeId, month.atDay(1), new AttendanceRequest(DayStatus.FULL, null, null, null));
            payrollService.finalizeMonth(month);
            months.add(month);
        }
        return months;
    }

    private BigDecimal advanceBalance() {
        return jdbcTemplate.queryForObject("SELECT advance_balance FROM employee WHERE id = ?", BigDecimal.class, employeeId);
    }

    private BigDecimal sum(String sql) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, employeeId);
    }

    @FunctionalInterface
    private interface PayAction {
        Object run(YearMonth month) throws Exception;
    }
}
