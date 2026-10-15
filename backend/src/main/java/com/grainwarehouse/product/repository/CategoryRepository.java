package com.grainwarehouse.product.repository;

import com.grainwarehouse.product.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Trả về bản ghi (thay vì boolean) để thông báo lỗi nêu được trạng thái ngừng dùng
    Optional<Category> findByNameIgnoreCase(String name);

    Optional<Category> findByNameIgnoreCaseAndIdNot(String name, Long id);
}
