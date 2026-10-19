package com.grainwarehouse.attendance.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

/**
 * Khóa theo THÁNG cho mọi thao tác làm đổi bảng công hoặc bảng lương của tháng đó.
 *
 * Vì sao không dùng SELECT ... FOR UPDATE như phiếu nhập: chấm công một ô mới thì chưa có dòng nào để khóa,
 * và chốt lương cần chặn cả việc THÊM ô mới vào tháng. pg_advisory_xact_lock khóa một "con số" do ta tự đặt
 * (ở đây: loại khóa + yyyymm), tự nhả khi transaction kết thúc (commit hay rollback đều nhả).
 * Mọi đường ghi phải đi qua đây thì khóa mới có tác dụng.
 */
@Component
@RequiredArgsConstructor
public class AttendanceMonthLock {

    /** Không gian khóa riêng của chấm công — để không đụng khóa advisory của tính năng khác sau này. */
    private static final int LOCK_SPACE = 4201;

    private final JdbcTemplate jdbcTemplate;

    // MANDATORY: khóa xact chỉ có nghĩa bên trong transaction của người gọi (giống InventoryService)
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(YearMonth month) {
        int key = month.getYear() * 100 + month.getMonthValue();
        jdbcTemplate.queryForObject("SELECT pg_advisory_xact_lock(?, ?)::text", String.class, LOCK_SPACE, key);
    }
}
