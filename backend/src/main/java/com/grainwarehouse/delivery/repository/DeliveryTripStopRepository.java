package com.grainwarehouse.delivery.repository;

import com.grainwarehouse.delivery.entity.DeliveryTripStop;
import com.grainwarehouse.inventory.entity.StockDocument;
import com.grainwarehouse.inventory.entity.StockDocumentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DeliveryTripStopRepository extends JpaRepository<DeliveryTripStop, Long> {

    /** BR-DLV-03: phiếu nào đã nằm trên chuyến nào — để báo "PX000123 đã ở chuyến CG000004". */
    @Query("select s from DeliveryTripStop s join fetch s.trip where s.document.id in :documentIds")
    List<DeliveryTripStop> findByDocumentIds(@Param("documentIds") Collection<Long> documentIds);

    /** Điểm giao của nhiều chuyến (trang danh sách: đếm điểm, cộng tấn). */
    @Query("select s from DeliveryTripStop s join fetch s.document where s.trip.id in :tripIds")
    List<DeliveryTripStop> findByTripIds(@Param("tripIds") Collection<Long> tripIds);

    /**
     * BR-DLV-03: phiếu bán xếp được vào chuyến = Nháp và chưa nằm trên chuyến nào. Ngày khách muốn giao sớm nhất
     * lên đầu, phiếu không ghi ngày giao xuống cuối.
     */
    @Query("""
            select d from StockDocument d join fetch d.customer join fetch d.warehouse
            where d.type = com.grainwarehouse.inventory.entity.StockDocumentType.OUTBOUND
              and d.status = com.grainwarehouse.inventory.entity.StockDocumentStatus.DRAFT
              and not exists (select s.id from DeliveryTripStop s where s.document = d)
            order by d.requestedDeliveryDate asc nulls last, d.id asc
            """)
    List<StockDocument> findAssignableOrders();

    /** Dòng hàng của nhiều phiếu trong một câu — tránh N+1 khi hiển thị chuyến 20 điểm giao. */
    @Query("""
            select i from StockDocumentItem i join fetch i.product p join fetch p.unit
            where i.document.id in :documentIds
            order by i.id
            """)
    List<StockDocumentItem> findItemsOfDocuments(@Param("documentIds") Collection<Long> documentIds);
}
