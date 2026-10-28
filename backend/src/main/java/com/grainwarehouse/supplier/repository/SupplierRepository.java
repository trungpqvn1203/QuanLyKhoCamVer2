package com.grainwarehouse.supplier.repository;

import com.grainwarehouse.supplier.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Long>, JpaSpecificationExecutor<Supplier> {

    // Trả về bản ghi (thay vì boolean) để thông báo lỗi nêu được mã NCC và trạng thái
    Optional<Supplier> findByNameIgnoreCase(String name);

    Optional<Supplier> findByNameIgnoreCaseAndIdNot(String name, Long id);

    // BR-SUP-09: MST đã chuẩn hóa nên so bằng nhau, không cần IgnoreCase
    Optional<Supplier> findByTaxCode(String taxCode);

    Optional<Supplier> findByTaxCodeAndIdNot(String taxCode, Long id);

    // Cột "Nhà cung cấp" của file Excel sản phẩm ghi mã NCC (BR-PRD-10)
    Optional<Supplier> findByCode(String code);
}
