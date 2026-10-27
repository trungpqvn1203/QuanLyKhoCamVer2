package com.grainwarehouse.opening.dto;

import com.grainwarehouse.inventory.entity.StockDocumentType;

/** Công nợ đầu kỳ của ai: khách nợ mình (thu) hay mình nợ NCC (trả). Mỗi loại một loại chứng từ, một tiền tố mã. */
public enum OpeningDebtParty {
    CUSTOMER(StockDocumentType.OPENING_CUSTOMER_DEBT, "DKH%06d", "khách hàng"),
    SUPPLIER(StockDocumentType.OPENING_SUPPLIER_DEBT, "DNC%06d", "nhà cung cấp");

    private final StockDocumentType documentType;
    private final String codeFormat;
    private final String label;

    OpeningDebtParty(StockDocumentType documentType, String codeFormat, String label) {
        this.documentType = documentType;
        this.codeFormat = codeFormat;
        this.label = label;
    }

    public StockDocumentType documentType() {
        return documentType;
    }

    public String codeFormat() {
        return codeFormat;
    }

    /** "khách hàng" / "nhà cung cấp" — ghép vào thông báo lỗi. */
    public String label() {
        return label;
    }
}
