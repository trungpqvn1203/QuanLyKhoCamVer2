package com.grainwarehouse.product.repository;

import com.grainwarehouse.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // @EntityGraph: JOIN lấy luôn category trong cùng một câu SQL.
    // Không có nó: 1 query lấy 20 sản phẩm + 20 query lấy category = lỗi N+1.
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Pageable pageable);

    // Danh sách có lọc (ProductSpecifications) — cũng cần JOIN category để tránh N+1
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    // Trả về bản ghi (thay vì boolean) để thông báo lỗi nêu được mã SP và trạng thái
    Optional<Product> findByNameIgnoreCase(String name);

    Optional<Product> findByNameIgnoreCaseAndIdNot(String name, Long id);
}
