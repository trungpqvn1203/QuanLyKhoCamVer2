package com.grainwarehouse.cashbook.repository;

import com.grainwarehouse.cashbook.entity.CashBookChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Chỉ thêm và đọc — lịch sử đặt/sửa đầu kỳ bất biến (trigger ở DB chặn nếu lỡ gọi sửa/xóa). */
public interface CashBookChangeRepository extends JpaRepository<CashBookChange, Long> {

    List<CashBookChange> findAllByOrderByIdDesc();
}
