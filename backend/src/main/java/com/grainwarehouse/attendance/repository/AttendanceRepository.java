package com.grainwarehouse.attendance.repository;

import com.grainwarehouse.attendance.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    /** Mọi ô chấm công trong [from, to], kèm nhân viên (JOIN FETCH, tránh N+1 khi dựng bảng công). */
    @Query("""
            select a from Attendance a join fetch a.employee
            where a.workDate between :from and :to
            order by a.workDate
            """)
    List<Attendance> findInRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
