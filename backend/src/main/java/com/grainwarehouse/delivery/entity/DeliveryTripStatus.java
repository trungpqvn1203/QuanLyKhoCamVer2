package com.grainwarehouse.delivery.entity;

/** BR-DLV-02: Đang xếp → Đã xuất. Không có "Đã hủy": chuyến Đang xếp bỏ đi thì xóa hẳn (BR-DLV-09). */
public enum DeliveryTripStatus {
    PLANNED("Đang xếp"),
    DISPATCHED("Đã xuất");

    private final String label;

    DeliveryTripStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
