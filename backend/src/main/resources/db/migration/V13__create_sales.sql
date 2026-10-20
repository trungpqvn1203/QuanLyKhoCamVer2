-- Migration V13: Bán hàng / xuất kho — phiếu bán (mở loại OUTBOUND), bảng giá theo khách, thanh toán & công nợ
-- Quy tắc: BR-SAL-01..15 (docs/business/rules.md)

-- ===== Phiếu bán: dùng chung stock_document với phiếu nhập =====

-- BR-SAL-03: mã phiếu bán PX + 6 chữ số. Như PN, service gọi nextval rồi format (cột code không có DEFAULT).
CREATE SEQUENCE outbound_code_seq;

ALTER TABLE stock_document
    ADD COLUMN customer_id BIGINT,
    -- BR-SAL-10: tổng đã thu (snapshot) — nhật ký đầy đủ nằm ở sale_payment, giống inventory ↔ stock_transaction
    ADD COLUMN paid_amount NUMERIC(18,0) NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_stock_document_customer FOREIGN KEY (customer_id) REFERENCES customer (id);

ALTER TABLE stock_document DROP CONSTRAINT ck_stock_document_type;
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_type CHECK (type IN ('INBOUND', 'OUTBOUND'));

-- Phiếu nhập: có NCC, không có khách. Phiếu bán: có khách (BR-SAL-01), không có NCC.
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_customer CHECK (
    (type <> 'INBOUND' OR customer_id IS NULL)
    AND (type <> 'OUTBOUND' OR (customer_id IS NOT NULL AND supplier_id IS NULL)));

-- BR-SAL-11: không bao giờ thu quá số tiền phiếu — tuyến phòng thủ cuối, kể cả khi code quên kiểm tra
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_paid CHECK (paid_amount >= 0 AND paid_amount <= total_amount);

-- Phiếu của một khách theo ngày: lịch sử mua và công nợ theo khách
CREATE INDEX idx_stock_document_customer ON stock_document (customer_id, document_date);

-- ===== Bảng giá riêng theo khách (BR-SAL-06..08) =====

-- Một giá hiện hành cho mỗi cặp (khách, SP). Chỉ dùng để ĐIỀN SẴN giá khi lập phiếu;
-- giá thực bán chụp lại trên stock_document_item.unit_price nên đổi bảng giá không đổi phiếu cũ.
CREATE TABLE customer_product_price (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id  BIGINT        NOT NULL,
    product_id   BIGINT        NOT NULL,
    unit_price   NUMERIC(15,2) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Index này cũng phục vụ "bảng giá của khách X" (customer_id đứng đầu)
    CONSTRAINT uk_customer_product_price UNIQUE (customer_id, product_id),
    CONSTRAINT ck_customer_product_price CHECK (unit_price >= 0),
    CONSTRAINT fk_customer_product_price_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_customer_product_price_product FOREIGN KEY (product_id) REFERENCES product (id)
);

-- ===== Thanh toán của phiếu bán — BẤT BIẾN (BR-SAL-09..14) =====

CREATE TABLE sale_payment (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id     BIGINT        NOT NULL,
    type            VARCHAR(10)   NOT NULL,
    -- BR-SAL-12: trả kết hợp tiền mặt + chuyển khoản = hai dòng, mỗi dòng một hình thức
    method          VARCHAR(20)   NOT NULL,
    amount          NUMERIC(18,0) NOT NULL,
    payment_date    DATE          NOT NULL,
    note            VARCHAR(500),
    -- BR-SAL-13: dòng hoàn trỏ về khoản thu gốc
    reversal_of_id  BIGINT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_sale_payment_type CHECK (type IN ('PAYMENT', 'REVERSAL')),
    CONSTRAINT ck_sale_payment_method CHECK (method IN ('CASH', 'BANK_TRANSFER')),
    CONSTRAINT ck_sale_payment_amount CHECK (amount > 0),
    -- Dòng hoàn bắt buộc trỏ về khoản gốc; khoản thu thường thì không
    CONSTRAINT ck_sale_payment_reversal CHECK ((type = 'REVERSAL') = (reversal_of_id IS NOT NULL)),
    -- Mỗi khoản thu chỉ được hoàn một lần
    CONSTRAINT uk_sale_payment_reversal_of UNIQUE (reversal_of_id),
    CONSTRAINT fk_sale_payment_document FOREIGN KEY (document_id) REFERENCES stock_document (id),
    CONSTRAINT fk_sale_payment_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES sale_payment (id)
);

-- Lịch sử thanh toán của một phiếu
CREATE INDEX idx_sale_payment_document ON sale_payment (document_id);

-- Chặn sửa/xóa ở DB như stock_transaction. Hàm riêng để thông báo nêu đúng bảng và quy tắc.
CREATE FUNCTION prevent_sale_payment_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'sale_payment là bất biến: không được % (BR-SAL-13)', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_sale_payment_immutable
    BEFORE UPDATE OR DELETE ON sale_payment
    FOR EACH ROW EXECUTE FUNCTION prevent_sale_payment_change();

CREATE TRIGGER trg_sale_payment_no_truncate
    BEFORE TRUNCATE ON sale_payment
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_sale_payment_change();
