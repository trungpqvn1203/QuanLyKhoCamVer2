package com.grainwarehouse.cashbook.repository;

import com.grainwarehouse.cashbook.entity.CashCategory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashCategoryRepository extends JpaRepository<CashCategory, Long> {

    // Trả về bản ghi (thay vì boolean) để thông báo lỗi nêu được trạng thái ngừng dùng
    Optional<CashCategory> findByNameIgnoreCase(String name);

    Optional<CashCategory> findByNameIgnoreCaseAndIdNot(String name, Long id);

    /** Không phân trang: danh mục thu/chi chỉ vài chục mục. */
    List<CashCategory> findAll(Sort sort);
}
