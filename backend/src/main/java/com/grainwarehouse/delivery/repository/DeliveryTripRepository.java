package com.grainwarehouse.delivery.repository;

import com.grainwarehouse.delivery.entity.DeliveryTrip;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeliveryTripRepository extends JpaRepository<DeliveryTrip, Long>, JpaSpecificationExecutor<DeliveryTrip> {

    /**
     * SELECT ... FOR UPDATE: mọi thao tác đổi chuyến (sửa, xóa, xuất) khóa chuyến TRƯỚC rồi mới kiểm trạng thái —
     * hai người cùng bấm Xuất chuyến thì người sau chờ, rồi thấy "Đã xuất" và bị từ chối (như BR-IMP-14).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DeliveryTrip t where t.id = :id")
    Optional<DeliveryTrip> findForUpdate(@Param("id") Long id);

    // Màn chi tiết: xe, lái xe, điểm giao + phiếu + khách + kho trong một lần (dòng hàng tải riêng — xem service)
    @EntityGraph(attributePaths = {"vehicle", "driver", "stops", "stops.document", "stops.document.customer",
            "stops.document.warehouse"})
    Optional<DeliveryTrip> findWithDetailsById(Long id);

    @Override
    @EntityGraph(attributePaths = {"vehicle", "driver"})
    Page<DeliveryTrip> findAll(Specification<DeliveryTrip> spec, Pageable pageable);

    // BR-DLV-01: mã chuyến CG
    @Query(value = "SELECT nextval('delivery_trip_code_seq')", nativeQuery = true)
    long nextCodeNumber();
}
