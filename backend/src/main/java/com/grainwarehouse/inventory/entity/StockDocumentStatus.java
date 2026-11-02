package com.grainwarehouse.inventory.entity;

/**
 * Trạng thái chứng từ (BR-IMP-11, BR-IMP-12). Bước chuyển hợp lệ:
 * DRAFT → COMPLETED, DRAFT → CANCELLED, COMPLETED → REVERSED; riêng phiếu bán có thêm
 * PENDING → DRAFT (xác nhận đơn khách đặt) và PENDING → CANCELLED (BR-SAL-16).
 * CANCELLED và REVERSED là trạng thái cuối.
 */
public enum StockDocumentStatus {
    PENDING("Chờ xác nhận"),
    DRAFT("Nháp"),
    COMPLETED("Hoàn tất"),
    CANCELLED("Đã hủy"),
    REVERSED("Đã đảo");

    private final String label;

    StockDocumentStatus(String label) {
        this.label = label;
    }

    /** Tên tiếng Việt để ghép vào thông báo lỗi. */
    public String label() {
        return label;
    }
}
