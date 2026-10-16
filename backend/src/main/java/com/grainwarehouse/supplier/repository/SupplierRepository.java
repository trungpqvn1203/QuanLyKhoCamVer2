package com.grainwarehouse.supplier.repository;

import com.grainwarehouse.supplier.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    // Trả về bản ghi (thay vì boolean) để thông báo lỗi nêu được mã NCC và trạng thái
    Optional<Supplier> findByNameIgnoreCase(String name);

    Optional<Supplier> findByNameIgnoreCaseAndIdNot(String name, Long id);
}
