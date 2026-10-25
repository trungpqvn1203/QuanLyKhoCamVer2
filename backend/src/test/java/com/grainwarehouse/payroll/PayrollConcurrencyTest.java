package com.grainwarehouse.payroll;

import com.grainwarehouse.attendance.dto.AttendanceRequest;
import com.grainwarehouse.attendance.entity.DayStatus;
import com.grainwarehouse.attendance.service.AttendanceService;
import com.grainwarehouse.common.exception.BusinessException;
import com.grainwarehouse.common.search.CommonSpecifications;
import com.grainwarehouse.payroll.dto.PayrollSheetResponse;
import com.grainwarehouse.payroll.service.PayrollService;
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
 * BR-PAY-03 khi có đồng thời: chấm công chen vào đúng lúc đang chốt lương.
 * Mỗi lần chấm công hoặc thành công TRƯỚC khi chốt (nằm trong bản chụp), hoặc bị từ chối vì đã chốt —
 * không được có ô nào lọt vào bảng công mà bản chụp không thấy. Kiểm bằng: tổng tấn bản chụp = tổng tấn trong DB.
 * Không @Transactional (cần commit thật) → tự dọn ở @AfterEach.
 */
@SpringBootTest
@ActiveProfiles("test")
class PayrollConcurrencyTest {

    private static final int ROUNDS = 8;
    private static final int WRITERS = 6;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long employeeId;

    @BeforeEach
    void setUp() {
        employeeId = jdbcTemplate.queryForObject(
                "INSERT INTO employee (name, unload_ton_rate) VALUES ('NV chốt đồng thời', 50000) RETURNING id", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM payroll_item WHERE employee_id = ?", employeeId);
        jdbcTemplate.update("DELETE FROM payroll WHERE id NOT IN (SELECT payroll_id FROM payroll_item)");
        jdbcTemplate.update("DELETE FROM attendance WHERE employee_id = ?", employeeId);
        jdbcTemplate.update("DELETE FROM employee WHERE id = ?", employeeId);
    }

    @Test
    @DisplayName("Chấm công song song với chốt lương - bản chụp luôn khớp đúng bảng công")
    void snapshotShouldMatchAttendanceWhenWritesRaceWithFinalize() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(WRITERS + 1);
        try {
            // Mỗi vòng một tháng riêng (lùi dần từ tháng trước) để vòng sau không dính khóa của vòng trước
            for (int round = 1; round <= ROUNDS; round++) {
                YearMonth month = YearMonth.now(CommonSpecifications.BUSINESS_ZONE).minusMonths(round);
                attendanceService.record(employeeId, month.atDay(1), new AttendanceRequest(DayStatus.FULL, BigDecimal.ONE, null, null));

                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> writers = new ArrayList<>();
                for (int day = 2; day < 2 + WRITERS; day++) {
                    int d = day;
                    writers.add(pool.submit(() -> {
                        start.await();
                        return attendanceService.record(employeeId, month.atDay(d),
                                new AttendanceRequest(DayStatus.FULL, BigDecimal.ONE, null, null));
                    }));
                }
                Future<PayrollSheetResponse> finalizer = pool.submit(() -> {
                    start.await();
                    return payrollService.finalizeMonth(month);
                });
                start.countDown();

                for (Future<?> writer : writers) {
                    try {
                        writer.get(30, TimeUnit.SECONDS);
                    } catch (ExecutionException e) {
                        // Bị từ chối vì đã chốt là hợp lệ; lỗi khác thì không
                        assertThat(e.getCause()).isInstanceOf(BusinessException.class);
                    }
                }
                PayrollSheetResponse snapshot = finalizer.get(30, TimeUnit.SECONDS);

                BigDecimal tonsInDb = jdbcTemplate.queryForObject(
                        "SELECT sum(unload_tons) FROM attendance WHERE employee_id = ? AND work_date BETWEEN ? AND ?",
                        BigDecimal.class, employeeId, month.atDay(1), month.atEndOfMonth());
                assertThat(snapshot.items().getFirst().unloadTons()).isEqualByComparingTo(tonsInDb);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
