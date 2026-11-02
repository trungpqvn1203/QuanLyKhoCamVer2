package com.grainwarehouse.sales.repository;

import com.grainwarehouse.sales.entity.SaleRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SaleRequestItemRepository extends JpaRepository<SaleRequestItem, Long> {

    // Kèm SP và đơn vị tính để hiện "khách đặt" mà không N+1
    @Query("""
            select r from SaleRequestItem r join fetch r.product p join fetch p.unit
            where r.document.id = :documentId order by r.id
            """)
    List<SaleRequestItem> findByDocumentId(@Param("documentId") Long documentId);
}
