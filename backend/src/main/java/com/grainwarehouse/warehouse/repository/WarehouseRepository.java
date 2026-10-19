package com.grainwarehouse.warehouse.repository;

import com.grainwarehouse.warehouse.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long>, JpaSpecificationExecutor<Warehouse> {

    // Trả về bản ghi (thay vì boolean) để thông báo lỗi nêu được mã kho và trạng thái
    Optional<Warehouse> findByNameIgnoreCase(String name);

    Optional<Warehouse> findByNameIgnoreCaseAndIdNot(String name, Long id);
}
