package com.grainwarehouse.delivery;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ràng buộc của V31 (xe, chuyến giao) ở tầng DB — ghi thẳng SQL để chứng minh chính DB chặn khi code Java quên.
 * Mỗi test chỉ một câu vi phạm: sau lỗi đầu tiên PostgreSQL hủy transaction (SQL state 25P02).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeliveryPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long vehicleId;
    private Long tripId;
    private Long firstDocumentId;
    private Long secondDocumentId;

    @BeforeEach
    void setUp() {
        Long warehouseId = id("INSERT INTO warehouse (name, type, address) VALUES ('Kho chuyến DB', 'CENTRAL', 'HN')"
                + " RETURNING id");
        Long customerId = id("INSERT INTO customer (name, type, phone) VALUES ('Khách chuyến DB', 'DEALER', '0909888001')"
                + " RETURNING id");
        Long employeeId = id("INSERT INTO employee (name, daily_wage) VALUES ('Lái xe DB', 300000) RETURNING id");
        vehicleId = id("INSERT INTO vehicle (plate_number, capacity_tons) VALUES ('29H-123.45', 5) RETURNING id");
        firstDocumentId = draft("PX-DLV-1", warehouseId, customerId);
        secondDocumentId = draft("PX-DLV-2", warehouseId, customerId);
        tripId = trip("CG-DB-1", employeeId);
        stop(tripId, firstDocumentId, 1);
        stop(tripId, secondDocumentId, 2);
    }

    @Test
    @DisplayName("BR-VEH-01: DB chặn cùng biển số gõ khác cách (hoa thường, khoảng trắng, chấm, gạch)")
    void dbShouldRejectSamePlateWrittenDifferently() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO vehicle (plate_number) VALUES ('29h 12345')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_vehicle_plate");
    }

    @Test
    @DisplayName("BR-VEH-02: DB chặn tải trọng <= 0")
    void dbShouldRejectNonPositiveCapacity() {
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE vehicle SET capacity_tons = 0 WHERE id = ?", vehicleId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_vehicle_capacity");
    }

    @Test
    @DisplayName("BR-DLV-03: DB chặn một phiếu nằm trên hai chuyến")
    void dbShouldRejectDocumentOnTwoTrips() {
        Long employeeId = jdbcTemplate.queryForObject("SELECT driver_id FROM delivery_trip WHERE id = ?", Long.class, tripId);
        Long otherTrip = trip("CG-DB-2", employeeId);

        assertThatThrownBy(() -> stop(otherTrip, firstDocumentId, 1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_delivery_trip_stop_document");
    }

    @Test
    @DisplayName("BR-DLV-05: đổi chỗ hai điểm giao bằng hai câu UPDATE - giữa chừng trùng thứ tự nhưng hợp lệ lúc kiểm (DEFERRABLE)")
    void dbShouldAllowSwappingStopsWithinTransaction() {
        jdbcTemplate.update("UPDATE delivery_trip_stop SET stop_order = 1 WHERE document_id = ?", secondDocumentId);
        jdbcTemplate.update("UPDATE delivery_trip_stop SET stop_order = 2 WHERE document_id = ?", firstDocumentId);

        // Ép kiểm ngay (bình thường đợi tới COMMIT): không còn trùng → không lỗi
        jdbcTemplate.execute("SET CONSTRAINTS uk_delivery_trip_stop_order IMMEDIATE");
        assertThat(jdbcTemplate.queryForObject("SELECT stop_order FROM delivery_trip_stop WHERE document_id = ?",
                Integer.class, secondDocumentId)).isEqualTo(1);
    }

    @Test
    @DisplayName("BR-DLV-05: trùng thứ tự còn lại tới lúc kiểm thì DB vẫn chặn (DEFERRABLE chỉ hoãn, không bỏ)")
    void dbShouldRejectDuplicateStopOrderAtCheckTime() {
        jdbcTemplate.update("UPDATE delivery_trip_stop SET stop_order = 1 WHERE document_id = ?", secondDocumentId);

        assertThatThrownBy(() -> jdbcTemplate.execute("SET CONSTRAINTS uk_delivery_trip_stop_order IMMEDIATE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_delivery_trip_stop_order");
    }

    @Test
    @DisplayName("BR-DLV-07: DB chặn chuyến Đã xuất mà không ghi lúc nào / ai xuất")
    void dbShouldRequireDispatchInfo() {
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE delivery_trip SET status = 'DISPATCHED' WHERE id = ?", tripId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_delivery_trip_dispatched");
    }

    @Test
    @DisplayName("BR-DLV-09: xóa chuyến thì điểm giao bị xóa theo (ON DELETE CASCADE), phiếu bán còn nguyên")
    void dbShouldCascadeStopsWhenTripDeleted() {
        jdbcTemplate.update("DELETE FROM delivery_trip WHERE id = ?", tripId);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM delivery_trip_stop WHERE trip_id = ?",
                Integer.class, tripId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM stock_document WHERE id IN (?, ?)",
                Integer.class, firstDocumentId, secondDocumentId)).isEqualTo(2);
    }

    private Long draft(String code, Long warehouseId, Long customerId) {
        return id("INSERT INTO stock_document (code, type, order_channel, status, warehouse_id, customer_id, document_date)"
                + " VALUES (?, 'OUTBOUND', 'DIRECT', 'DRAFT', ?, ?, current_date) RETURNING id", code, warehouseId, customerId);
    }

    private Long trip(String code, Long employeeId) {
        return id("INSERT INTO delivery_trip (code, trip_date, vehicle_id, driver_id, created_by)"
                + " VALUES (?, current_date, ?, ?, 'test') RETURNING id", code, vehicleId, employeeId);
    }

    private void stop(Long trip, Long documentId, int order) {
        jdbcTemplate.update("INSERT INTO delivery_trip_stop (trip_id, document_id, stop_order) VALUES (?, ?, ?)",
                trip, documentId, order);
    }

    private Long id(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }
}
