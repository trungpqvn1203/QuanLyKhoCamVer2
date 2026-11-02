package com.grainwarehouse.sales.repository;

import com.grainwarehouse.sales.entity.SalePriceAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Chỉ thêm và đọc (BR-SAL-26) — trigger ở DB chặn sửa/xóa nếu lỡ gọi. */
public interface SalePriceAdjustmentRepository extends JpaRepository<SalePriceAdjustment, Long> {

    @Query("select a from SalePriceAdjustment a join fetch a.product where a.document.id = :documentId order by a.id")
    List<SalePriceAdjustment> findByDocumentId(@Param("documentId") Long documentId);
}
