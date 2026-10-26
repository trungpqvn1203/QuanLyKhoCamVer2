package com.grainwarehouse.cashbook.repository;

import com.grainwarehouse.cashbook.entity.CashBook;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CashBookRepository extends JpaRepository<CashBook, Short> {

    /** Khóa dòng đầu kỳ khi sửa: hai người cùng sửa thì người sau chờ, rồi chụp đúng giá trị cũ vào lịch sử. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from CashBook b where b.id = :id")
    Optional<CashBook> findForUpdate(@Param("id") Short id);
}
