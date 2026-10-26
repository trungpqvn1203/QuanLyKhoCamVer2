package com.grainwarehouse.attendance;

import com.grainwarehouse.attendance.dto.AttendanceRequest;
import com.grainwarehouse.attendance.entity.DayStatus;
import com.grainwarehouse.attendance.service.AttendanceService;
import com.grainwarehouse.common.search.CommonSpecifications;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Nhiều người cùng ghi MỘT ô chưa có. Không có khóa tháng: cả hai cùng "chưa thấy ô" → cùng INSERT →
 * người sau dính uk_attendance_employee_date (409). Có khóa: xếp hàng, người sau ghi đè — không ai lỗi.
 * Không @Transactional (cần commit thật để các luồng thấy nhau) → tự dọn ở @AfterEach.
 */
@SpringBootTest
@ActiveProfiles("test")
class AttendanceConcurrencyTest {

    private static final int THREADS = 5;
    private static final int DAYS = 10;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long employeeId;

    @BeforeEach
    void setUp() {
        employeeId = jdbcTemplate.queryForObject(
                "INSERT INTO employee (name, unload_ton_rate) VALUES ('NV đồng thời', 50000) RETURNING id", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM attendance WHERE employee_id = ?", employeeId);
        jdbcTemplate.update("DELETE FROM employee WHERE id = ?", employeeId);
    }

    @Test
    @DisplayName("5 luồng cùng chấm một ô mới, lặp 10 ngày - không luồng nào lỗi, mỗi ngày đúng 1 dòng")
    void concurrentWritesToSameNewCellShouldAllSucceed() throws Exception {
        YearMonth lastMonth = YearMonth.now(CommonSpecifications.BUSINESS_ZONE).minusMonths(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Throwable> failures = new ArrayList<>();
        try {
            for (int day = 1; day <= DAYS; day++) {
                LocalDate date = lastMonth.atDay(day);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    BigDecimal tons = BigDecimal.valueOf(i + 1);
                    futures.add(pool.submit(() -> {
                        start.await();
                        return attendanceService.record(employeeId, date,
                                new AttendanceRequest(DayStatus.FULL, tons, null, null));
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    try {
                        future.get(30, TimeUnit.SECONDS);
                    } catch (java.util.concurrent.ExecutionException e) {
                        failures.add(e.getCause());
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(failures).isEmpty();
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM attendance WHERE employee_id = ?", Integer.class, employeeId);
        assertThat(rows).isEqualTo(DAYS);
    }
}
