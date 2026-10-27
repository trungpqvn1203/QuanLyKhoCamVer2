package com.grainwarehouse.stockcount.repository;

import com.grainwarehouse.stockcount.entity.StockCountReason;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockCountReasonRepository extends JpaRepository<StockCountReason, Long> {

    // Trả về bản ghi (thay vì boolean) để thông báo lỗi nêu được trạng thái ngừng dùng
    Optional<StockCountReason> findByNameIgnoreCase(String name);

    Optional<StockCountReason> findByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Không phân trang: lý do lệch chỉ vài mục. */
    List<StockCountReason> findAll(Sort sort);
}
