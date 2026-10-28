package com.grainwarehouse.product.repository;

import com.grainwarehouse.product.entity.ProductUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductUnitRepository extends JpaRepository<ProductUnit, Long> {

    // Trả về bản ghi (thay vì boolean) để thông báo lỗi nêu được trạng thái ngừng dùng
    Optional<ProductUnit> findByNameIgnoreCase(String name);

    Optional<ProductUnit> findByNameIgnoreCaseAndIdNot(String name, Long id);

    // Dropdown "Đơn vị" trong file mẫu nhập Excel: chỉ đơn vị còn dùng được
    List<ProductUnit> findByActiveTrueOrderByNameAsc();
}
