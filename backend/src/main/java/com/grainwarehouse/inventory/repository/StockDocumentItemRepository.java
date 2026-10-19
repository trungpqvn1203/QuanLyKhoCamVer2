package com.grainwarehouse.inventory.repository;

import com.grainwarehouse.inventory.entity.StockDocumentItem;
import com.grainwarehouse.inventory.entity.StockDocumentStatus;
import com.grainwarehouse.inventory.entity.StockDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Chỉ dùng để ĐỌC tổng hợp. Dòng chứng từ được thêm/sửa qua StockDocument (cascade),
 * luôn sau khi khóa phiếu (BR-IMP-14) — không ghi trực tiếp qua repository này.
 */
public interface StockDocumentItemRepository extends JpaRepository<StockDocumentItem, Long> {

    /**
     * Hàng đang về theo SP (BR-STK-06): tổng SL đặt trên các phiếu cùng loại/trạng thái (phiếu nhập Nháp).
     * warehouseId null = mọi kho.
     */
    @Query("""
            select it.product.id as id, sum(it.orderedQuantity) as quantity
            from StockDocumentItem it
            where it.document.type = :type and it.document.status = :status
              and it.product.id in :productIds
              and (:warehouseId is null or it.document.warehouse.id = :warehouseId)
            group by it.product.id
            """)
    List<QuantityByKey> sumOrderedByProduct(@Param("productIds") Collection<Long> productIds,
                                            @Param("warehouseId") Long warehouseId,
                                            @Param("type") StockDocumentType type,
                                            @Param("status") StockDocumentStatus status);

    /** Hàng đang về của một SP, chia theo kho nhận. */
    @Query("""
            select it.document.warehouse.id as id, sum(it.orderedQuantity) as quantity
            from StockDocumentItem it
            where it.document.type = :type and it.document.status = :status and it.product.id = :productId
            group by it.document.warehouse.id
            """)
    List<QuantityByKey> sumOrderedByWarehouse(@Param("productId") Long productId,
                                              @Param("type") StockDocumentType type,
                                              @Param("status") StockDocumentStatus status);
}
