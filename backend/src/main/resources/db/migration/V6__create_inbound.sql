-- Migration V6: Chứng từ kho (mở loại phiếu nhập), tồn kho, giao dịch kho
-- Quy tắc: BR-INV-01..07, BR-MON-03..05, BR-IMP-01..14 (docs/business/rules.md)

-- BR-IMP-07: mã phiếu nhập PN + 6 chữ số. Không đặt DEFAULT ở cột code vì sau này
-- phiếu xuất (PX), điều chuyển (PC) dùng chung cột nhưng khác tiền tố → service gọi nextval rồi format.
CREATE SEQUENCE inbound_code_seq;

CREATE TABLE stock_document (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(20)   NOT NULL,
    type           VARCHAR(20)   NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    warehouse_id   BIGINT        NOT NULL,
    supplier_id    BIGINT,
    document_date  DATE          NOT NULL,
    note           VARCHAR(500),
    total_amount   NUMERIC(18,0) NOT NULL DEFAULT 0,
    reason         VARCHAR(500),
    completed_at   TIMESTAMPTZ,
    cancelled_at   TIMESTAMPTZ,
    reversed_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_stock_document_code UNIQUE (code),
    -- Hiện chỉ mở phiếu nhập; module xuất/điều chuyển sẽ nới CHECK này bằng migration mới
    CONSTRAINT ck_stock_document_type CHECK (type IN ('INBOUND')),
    CONSTRAINT ck_stock_document_status CHECK (status IN ('DRAFT', 'COMPLETED', 'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_stock_document_supplier CHECK (type <> 'INBOUND' OR supplier_id IS NOT NULL),
    CONSTRAINT ck_stock_document_total CHECK (total_amount >= 0),
    -- Thời điểm chuyển trạng thái phải đi kèm trạng thái (phiếu đã đảo vẫn giữ completed_at)
    CONSTRAINT ck_stock_document_completed_at CHECK (status NOT IN ('COMPLETED', 'REVERSED') OR completed_at IS NOT NULL),
    CONSTRAINT ck_stock_document_cancelled_at CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL),
    CONSTRAINT ck_stock_document_reversed_at CHECK (status <> 'REVERSED' OR reversed_at IS NOT NULL),
    CONSTRAINT fk_stock_document_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_stock_document_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id)
);

-- Lọc danh sách phiếu theo loại + ngày chứng từ (dự kiến, kiểm chứng EXPLAIN ANALYZE ở Tuần 3)
CREATE INDEX idx_stock_document_type_date ON stock_document (type, document_date);

CREATE TABLE stock_document_item (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id       BIGINT        NOT NULL,
    product_id        BIGINT        NOT NULL,
    -- BR-IMP-03: SL đặt (chỉ phiếu nhập dùng) và SL thực tế tác động tồn (= SL thực nhận)
    ordered_quantity  INTEGER,
    quantity          INTEGER       NOT NULL,
    unit_price        NUMERIC(15,2) NOT NULL,
    line_amount       NUMERIC(18,0) NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- BR-IMP-A05: mỗi SP một dòng trong một phiếu. Index này cũng phục vụ "các dòng của phiếu X"
    CONSTRAINT uk_stock_document_item_product UNIQUE (document_id, product_id),
    CONSTRAINT ck_stock_document_item_ordered CHECK (ordered_quantity IS NULL OR ordered_quantity > 0),
    CONSTRAINT ck_stock_document_item_quantity CHECK (quantity >= 0),
    CONSTRAINT ck_stock_document_item_price CHECK (unit_price >= 0),
    CONSTRAINT ck_stock_document_item_amount CHECK (line_amount >= 0),
    CONSTRAINT fk_stock_document_item_document FOREIGN KEY (document_id) REFERENCES stock_document (id),
    CONSTRAINT fk_stock_document_item_product FOREIGN KEY (product_id) REFERENCES product (id)
);

-- Snapshot tồn hiện tại theo cặp (SP, kho) — đọc nhanh; lịch sử đầy đủ nằm ở stock_transaction
CREATE TABLE inventory (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id    BIGINT      NOT NULL,
    warehouse_id  BIGINT      NOT NULL,
    quantity      INTEGER     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Đích của INSERT ... ON CONFLICT khi cộng tồn
    CONSTRAINT uk_inventory_product_warehouse UNIQUE (product_id, warehouse_id),
    -- BR-INV-03: tuyến phòng thủ cuối, kể cả khi code Java quên kiểm tra
    CONSTRAINT ck_inventory_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_inventory_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id)
);

-- BR-INV-04..06: lịch sử biến động — BẤT BIẾN
CREATE TABLE stock_transaction (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_item_id  BIGINT      NOT NULL,
    product_id        BIGINT      NOT NULL,
    warehouse_id      BIGINT      NOT NULL,
    type              VARCHAR(10) NOT NULL,
    quantity          INTEGER     NOT NULL,
    quantity_before   INTEGER     NOT NULL,
    quantity_after    INTEGER     NOT NULL,
    -- BR-INV-06: giao dịch đảo trỏ về giao dịch gốc
    reversal_of_id    BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_stock_transaction_type CHECK (type IN ('IN', 'OUT')),
    CONSTRAINT ck_stock_transaction_quantity CHECK (quantity > 0),
    CONSTRAINT ck_stock_transaction_snapshot CHECK (quantity_before >= 0 AND quantity_after >= 0),
    CONSTRAINT ck_stock_transaction_balance CHECK (
        (type = 'IN' AND quantity_after = quantity_before + quantity)
        OR (type = 'OUT' AND quantity_after = quantity_before - quantity)),
    -- Mỗi giao dịch chỉ bị đảo một lần (BR-IMP-12)
    CONSTRAINT uk_stock_transaction_reversal_of UNIQUE (reversal_of_id),
    CONSTRAINT fk_stock_transaction_item FOREIGN KEY (document_item_id) REFERENCES stock_document_item (id),
    CONSTRAINT fk_stock_transaction_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_stock_transaction_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouse (id),
    CONSTRAINT fk_stock_transaction_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES stock_transaction (id)
);

-- Lịch sử tồn theo SP + kho + thời gian; giao dịch của một dòng chứng từ
CREATE INDEX idx_stock_transaction_product_warehouse ON stock_transaction (product_id, warehouse_id, created_at);
CREATE INDEX idx_stock_transaction_item ON stock_transaction (document_item_id);

-- BR-INV-05: chặn sửa/xóa ở tầng DB, không chỉ dựa vào việc code không có hàm sửa.
-- ERRCODE restrict_violation (23001) → Spring dịch thành DataIntegrityViolationException.
CREATE FUNCTION prevent_stock_transaction_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'stock_transaction là bất biến: không được % (BR-INV-05)', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_stock_transaction_immutable
    BEFORE UPDATE OR DELETE ON stock_transaction
    FOR EACH ROW EXECUTE FUNCTION prevent_stock_transaction_change();

-- TRUNCATE không kích hoạt trigger FOR EACH ROW → chặn riêng
CREATE TRIGGER trg_stock_transaction_no_truncate
    BEFORE TRUNCATE ON stock_transaction
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_stock_transaction_change();
