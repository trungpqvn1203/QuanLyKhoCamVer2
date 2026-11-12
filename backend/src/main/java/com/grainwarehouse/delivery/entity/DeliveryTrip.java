package com.grainwarehouse.delivery.entity;

import com.grainwarehouse.common.entity.AuditableEntity;
import com.grainwarehouse.employee.entity.Employee;
import com.grainwarehouse.inventory.entity.StockDocument;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chuyến giao hàng (BR-DLV-01..10). Như StockDocument, entity không tự kiểm tra trạng thái: service khóa chuyến
 * (SELECT ... FOR UPDATE) rồi mới kiểm tra và đổi. Không có setStatus — chỉ markDispatched.
 */
@Getter
@Entity
@Table(name = "delivery_trip")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryTrip extends AuditableEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String code;

    @Setter
    @Column(name = "trip_date", nullable = false)
    private LocalDate tripDate;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Employee driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryTripStatus status = DeliveryTripStatus.PLANNED;

    @Setter
    @Column(length = 500)
    private String note;

    @Column(name = "created_by", nullable = false, length = 50, updatable = false)
    private String createdBy;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "dispatched_by", length = 50)
    private String dispatchedBy;

    // orphanRemoval: bỏ phiếu khỏi chuyến = DELETE điểm giao khi flush
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stopOrder")
    private List<DeliveryTripStop> stops = new ArrayList<>();

    public DeliveryTrip(String code, String createdBy) {
        this.code = code;
        this.createdBy = createdBy;
    }

    public List<DeliveryTripStop> getStops() {
        return Collections.unmodifiableList(stops);
    }

    /**
     * BR-DLV-05: điểm giao theo đúng thứ tự danh sách (vị trí 1, 2, 3...). Sửa TẠI CHỖ theo phiếu: phiếu còn giữ →
     * đổi vị trí (UPDATE), phiếu bỏ → DELETE, phiếu mới → INSERT. Không xóa-hết-thêm-lại: Hibernate INSERT trước
     * DELETE khi flush, phiếu vừa bỏ vừa thêm lại sẽ đụng UNIQUE(document_id) — UNIQUE đó không hoãn được kiểm tra.
     * Hai điểm đổi chỗ cho nhau thì giữa chừng trùng vị trí — uk_delivery_trip_stop_order DEFERRABLE lo phần đó.
     */
    public void replaceStops(List<StockDocument> orderedDocuments) {
        Map<Long, DeliveryTripStop> existing = stops.stream()
                .collect(Collectors.toMap(stop -> stop.getDocument().getId(), Function.identity()));
        List<Long> keep = orderedDocuments.stream().map(StockDocument::getId).toList();
        stops.removeIf(stop -> !keep.contains(stop.getDocument().getId()));

        for (int i = 0; i < orderedDocuments.size(); i++) {
            StockDocument document = orderedDocuments.get(i);
            DeliveryTripStop stop = existing.get(document.getId());
            if (stop == null) {
                stops.add(new DeliveryTripStop(this, document, i + 1));
            } else {
                stop.moveTo(i + 1);
            }
        }
        // Sắp lại trong bộ nhớ cho khớp @OrderBy (không xóa rồi thêm lại phần tử — orphanRemoval có thể DELETE nhầm)
        stops.sort(Comparator.comparingInt(DeliveryTripStop::getStopOrder));
    }

    public void markDispatched(Instant at, String by) {
        status = DeliveryTripStatus.DISPATCHED;
        dispatchedAt = at;
        dispatchedBy = by;
    }
}
