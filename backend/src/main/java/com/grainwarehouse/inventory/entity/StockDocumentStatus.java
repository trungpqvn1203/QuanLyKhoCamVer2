package com.grainwarehouse.inventory.entity;

/**
 * Trạng thái chứng từ (BR-IMP-11, BR-IMP-12). Chỉ có ba bước chuyển hợp lệ:
 * DRAFT → COMPLETED, DRAFT → CANCELLED, COMPLETED → REVERSED.
 * CANCELLED và REVERSED là trạng thái cuối.
 */
public enum StockDocumentStatus {
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
