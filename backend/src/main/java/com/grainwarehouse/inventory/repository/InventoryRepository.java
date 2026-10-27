package com.grainwarehouse.inventory.repository;

import com.grainwarehouse.inventory.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Cộng/trừ tồn bằng MỘT câu SQL nguyên tử thay vì "đọc → tính trong Java → ghi":
 * hai transaction cùng đọc 100 rồi cùng ghi 100 - 10 sẽ làm mất một lần trừ (lost update).
 * Câu UPDATE tự khóa dòng tồn đến hết transaction → transaction thứ hai phải chờ rồi đọc giá trị mới.
 * RETURNING trả tồn SAU khi đổi để ghi quantity_before/after của StockTransaction.
 * Không có @Modifying: @Modifying chỉ trả số dòng bị ảnh hưởng, không lấy được RETURNING.
 */
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * Cộng tồn; chưa có dòng (SP lần đầu vào kho) thì tạo mới. ON CONFLICT xử lý luôn trường hợp
     * hai phiếu cùng nhập SP mới vào cùng kho một lúc: không bị lỗi trùng UNIQUE, cộng đủ cả hai.
     */
    @Query(value = """
            INSERT INTO inventory (product_id, warehouse_id, quantity)
            VALUES (:productId, :warehouseId, :quantity)
            ON CONFLICT (product_id, warehouse_id)
            DO UPDATE SET quantity = inventory.quantity + EXCLUDED.quantity, updated_at = now()
            RETURNING quantity
            """, nativeQuery = true)
    int increase(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId,
                 @Param("quantity") int quantity);

    /**
     * Trừ tồn chỉ khi đủ (BR-INV-01, BR-INV-02): kiểm tra và trừ trong cùng một câu lệnh.
     * Rỗng = không đủ tồn (hoặc chưa có dòng tồn) → service báo lỗi, transaction rollback.
     */
    @Query(value = """
            UPDATE inventory
            SET quantity = quantity - :quantity, updated_at = now()
            WHERE product_id = :productId AND warehouse_id = :warehouseId AND quantity >= :quantity
            RETURNING quantity
            """, nativeQuery = true)
    Optional<Integer> decreaseIfEnough(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId,
                                       @Param("quantity") int quantity);

    @Query(value = "SELECT quantity FROM inventory WHERE product_id = :productId AND warehouse_id = :warehouseId",
            nativeQuery = true)
    Optional<Integer> findQuantity(@Param("productId") Long productId, @Param("warehouseId") Long warehouseId);

    /**
     * Tổng tồn theo SP cho một trang danh sách — MỘT câu GROUP BY cho cả trang thay vì một câu mỗi SP (N+1).
     * warehouseId null = mọi kho, kể cả kho đã ngừng (BR-STK-01). SP chưa có dòng tồn nào thì không có trong kết quả.
     */
    @Query("""
            select i.product.id as id, sum(i.quantity) as quantity
            from Inventory i
            where i.product.id in :productIds and (:warehouseId is null or i.warehouse.id = :warehouseId)
            group by i.product.id
            """)
    List<QuantityByKey> sumByProduct(@Param("productIds") Collection<Long> productIds,
                                     @Param("warehouseId") Long warehouseId);

    /** Kiểm kê (BR-CNT-02): mọi SP đang có tồn > 0 tại kho, kèm SP — nạp sẵn dòng cho phiếu kiểm mới. */
    @Query("""
            select i from Inventory i join fetch i.product p
            where i.warehouse.id = :warehouseId and i.quantity > 0
            order by p.code
            """)
    List<Inventory> findInStockByWarehouse(@Param("warehouseId") Long warehouseId);

    /** Tồn hiện tại của các SP tại một kho (SP chưa có dòng tồn thì không có trong kết quả = tồn 0). */
    @Query("""
            select i.product.id as id, i.quantity as quantity from Inventory i
            where i.warehouse.id = :warehouseId and i.product.id in :productIds
            """)
    List<QuantityByKey> findQuantities(@Param("warehouseId") Long warehouseId,
                                       @Param("productIds") Collection<Long> productIds);

    // Tồn từng kho của một SP — dùng index uk_inventory_product_warehouse (product_id đứng đầu)
    @Query("select i from Inventory i join fetch i.warehouse where i.product.id = :productId")
    List<Inventory> findByProductIdWithWarehouse(@Param("productId") Long productId);
}
