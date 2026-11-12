package com.grainwarehouse.delivery.repository;

import com.grainwarehouse.delivery.entity.Vehicle;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    /**
     * BR-VEH-01: xe trùng biển số — so sánh đúng biểu thức của uk_vehicle_plate (V31) để báo lỗi rõ trước khi
     * đụng UNIQUE ở DB. normalized = biển số đã viết hoa, bỏ khoảng trắng / chấm / gạch (VehicleService.normalize).
     */
    @Query(value = """
            SELECT * FROM vehicle
            WHERE upper(regexp_replace(plate_number, '[[:space:].-]', '', 'g')) = :normalized
              AND (CAST(:excludeId AS BIGINT) IS NULL OR id <> :excludeId)
            """, nativeQuery = true)
    Optional<Vehicle> findByNormalizedPlate(@Param("normalized") String normalized, @Param("excludeId") Long excludeId);

    /** Ô chọn xe khi lập chuyến — vài chiếc, không phân trang. */
    List<Vehicle> findByActiveTrue(Sort sort);
}
