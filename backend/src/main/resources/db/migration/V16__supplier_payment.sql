-- Migration V16: trả tiền nhà cung cấp và công nợ NCC (BR-SPAY-01..05, xác nhận 2026-10-25)

-- ===== Phiếu nào phát sinh công nợ (BR-SPAY-05) =====

-- Phiếu nhập đã hoàn tất TRƯỚC module này coi như đã trả đủ: không hiện ở công nợ, không ghi trả tiền được.
-- Dùng cờ thay vì gán paid_amount = total_amount: gán như vậy tạo "đã trả" không có dòng thanh toán nào
-- giải thích (tổng không khớp nhật ký), và phiếu cũ sẽ bị khóa đảo theo BR-SPAY-04.
-- Phiếu bán và phiếu nhập tạo từ nay về sau: DEFAULT TRUE.
ALTER TABLE stock_document ADD COLUMN tracks_debt BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE stock_document SET tracks_debt = FALSE
 WHERE type = 'INBOUND' AND status IN ('COMPLETED', 'REVERSED');

-- paid_amount (V13) dùng chung: phiếu bán = đã thu, phiếu nhập = đã trả. CHECK 0 ≤ paid ≤ total đã có.

-- Công nợ theo NCC (BR-SPAY-01)
CREATE INDEX idx_stock_document_supplier ON stock_document (supplier_id, document_date);

-- ===== Thanh toán cho NCC — BẤT BIẾN (BR-SPAY-02, 03) =====
-- Cấu trúc giống sale_payment nhưng là bảng riêng: chiều tiền (chi/thu) và quyền khác nhau,
-- và không phải sửa module bán hàng đã duyệt.

CREATE TABLE supplier_payment (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id     BIGINT        NOT NULL,
    type            VARCHAR(10)   NOT NULL,
    -- Trả kết hợp tiền mặt + chuyển khoản = hai dòng, mỗi dòng một hình thức
    method          VARCHAR(20)   NOT NULL,
    amount          NUMERIC(18,0) NOT NULL,
    payment_date    DATE          NOT NULL,
    note            VARCHAR(500),
    -- BR-SPAY-03: dòng hoàn trỏ về khoản trả gốc
    reversal_of_id  BIGINT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_supplier_payment_type CHECK (type IN ('PAYMENT', 'REVERSAL')),
    CONSTRAINT ck_supplier_payment_method CHECK (method IN ('CASH', 'BANK_TRANSFER')),
    CONSTRAINT ck_supplier_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_supplier_payment_reversal CHECK ((type = 'REVERSAL') = (reversal_of_id IS NOT NULL)),
    -- Mỗi khoản trả chỉ được hoàn một lần
    CONSTRAINT uk_supplier_payment_reversal_of UNIQUE (reversal_of_id),
    CONSTRAINT fk_supplier_payment_document FOREIGN KEY (document_id) REFERENCES stock_document (id),
    CONSTRAINT fk_supplier_payment_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES supplier_payment (id)
);

CREATE INDEX idx_supplier_payment_document ON supplier_payment (document_id);

CREATE FUNCTION prevent_supplier_payment_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'supplier_payment là bất biến: không được % (BR-SPAY-03)', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_supplier_payment_immutable
    BEFORE UPDATE OR DELETE ON supplier_payment
    FOR EACH ROW EXECUTE FUNCTION prevent_supplier_payment_change();

CREATE TRIGGER trg_supplier_payment_no_truncate
    BEFORE TRUNCATE ON supplier_payment
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_supplier_payment_change();
