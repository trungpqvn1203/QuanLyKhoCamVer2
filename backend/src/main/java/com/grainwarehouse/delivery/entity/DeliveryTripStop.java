package com.grainwarehouse.delivery.entity;

import com.grainwarehouse.inventory.entity.StockDocument;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Một điểm giao của chuyến = một phiếu bán, ở vị trí stopOrder (1, 2, 3...). */
@Getter
@Entity
@Table(name = "delivery_trip_stop")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryTripStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, updatable = false)
    private DeliveryTrip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private StockDocument document;

    @Column(name = "stop_order", nullable = false)
    private int stopOrder;

    DeliveryTripStop(DeliveryTrip trip, StockDocument document, int stopOrder) {
        this.trip = trip;
        this.document = document;
        this.stopOrder = stopOrder;
    }

    void moveTo(int stopOrder) {
        this.stopOrder = stopOrder;
    }
}
