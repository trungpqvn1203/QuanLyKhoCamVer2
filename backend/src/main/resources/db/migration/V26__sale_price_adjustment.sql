-- Migration V26: Điều chỉnh giá phiếu bán đã giao, chưa thu tiền (BR-SAL-23..26, docs/business/rules.md)
-- Dòng phiếu và tổng phiếu được cập nhật tại chỗ (giá hiện hành); bảng này giữ lịch sử giá — BẤT BIẾN.
-- Số lượng không đổi nên stock_transaction không bị động tới.

CREATE TABLE sale_price_adjustment (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id       BIGINT        NOT NULL,
    document_item_id  BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    old_unit_price    NUMERIC(15,2) NOT NULL,
    new_unit_price    NUMERIC(15,2) NOT NULL,
    old_line_amount   NUMERIC(18,0) NOT NULL,
    new_line_amount   NUMERIC(18,0) NOT NULL,
    -- BR-SAL-24: lý do bắt buộc. Một lần điều chỉnh nhiều dòng → các dòng chung lý do và created_at
    reason            VARCHAR(500)  NOT NULL,
    adjusted_by       VARCHAR(50)   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_sale_price_adjustment_prices CHECK (old_unit_price >= 0 AND new_unit_price >= 0),
    -- Dòng nhật ký chỉ có khi giá thật sự đổi
    CONSTRAINT ck_sale_price_adjustment_changed CHECK (old_unit_price <> new_unit_price),
    CONSTRAINT ck_sale_price_adjustment_amounts CHECK (old_line_amount >= 0 AND new_line_amount >= 0),
    CONSTRAINT fk_sale_price_adjustment_document FOREIGN KEY (document_id) REFERENCES stock_document (id),
    CONSTRAINT fk_sale_price_adjustment_item FOREIGN KEY (document_item_id) REFERENCES stock_document_item (id),
    CONSTRAINT fk_sale_price_adjustment_product FOREIGN KEY (product_id) REFERENCES product (id)
);

-- Lịch sử điều chỉnh giá của một phiếu
CREATE INDEX idx_sale_price_adjustment_document ON sale_price_adjustment (document_id);

-- Chặn sửa/xóa ở DB như sale_payment (BR-SAL-26)
CREATE FUNCTION prevent_sale_price_adjustment_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'sale_price_adjustment là bất biến: không được % (BR-SAL-26)', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_sale_price_adjustment_immutable
    BEFORE UPDATE OR DELETE ON sale_price_adjustment
    FOR EACH ROW EXECUTE FUNCTION prevent_sale_price_adjustment_change();

CREATE TRIGGER trg_sale_price_adjustment_no_truncate
    BEFORE TRUNCATE ON sale_price_adjustment
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_sale_price_adjustment_change();
