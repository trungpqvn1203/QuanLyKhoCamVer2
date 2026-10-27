-- Migration V22: Kiểm kê kho (BR-CNT-01..10, docs/business/rules.md)
-- Phiếu kiểm kê là một loại chứng từ (STOCK_COUNT) trong stock_document. Mỗi dòng chụp SL sổ sách lúc bắt đầu kiểm
-- và SL đếm thực tế; xác nhận thì ghi PHẦN CHÊNH (thừa → IN, thiếu → OUT) qua cơ chế tồn kho chung.

-- Danh mục lý do lệch do người dùng tự quản lý (như danh mục thu/chi). Không xóa cứng, ngừng dùng bằng active
CREATE TABLE stock_count_reason (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uk_stock_count_reason_name ON stock_count_reason (lower(name));

-- Vài lý do thường gặp để dùng được ngay; đổi tên / ngừng dùng / thêm mới trên giao diện
INSERT INTO stock_count_reason (name) VALUES
    ('Hao hụt tự nhiên'),
    ('Hư hỏng, ẩm mốc'),
    ('Mất mát'),
    ('Ghi sót chứng từ');

ALTER TABLE stock_document DROP CONSTRAINT ck_stock_document_type;
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_type CHECK (type IN (
    'INBOUND', 'OUTBOUND', 'OPENING_STOCK', 'OPENING_CUSTOMER_DEBT', 'OPENING_SUPPLIER_DEBT', 'STOCK_COUNT'));

-- Kiểm kê có kho (ck_stock_document_warehouse đã bắt buộc), không NCC, không khách, không công nợ
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_stock_count CHECK (
    type <> 'STOCK_COUNT' OR (supplier_id IS NULL AND customer_id IS NULL AND NOT tracks_debt));

-- BR-CNT-03: thời điểm chụp SL sổ sách — giao dịch phát sinh SAU mốc này được cảnh báo lúc xác nhận
ALTER TABLE stock_document ADD COLUMN book_snapshot_at TIMESTAMPTZ;
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_snapshot CHECK (
    (type = 'STOCK_COUNT') = (book_snapshot_at IS NOT NULL));

-- BR-CNT-07: mỗi kho chỉ MỘT phiếu kiểm đang dở → không có hai phiếu cùng điều chỉnh một SP
CREATE UNIQUE INDEX uk_stock_document_open_count ON stock_document (warehouse_id)
    WHERE type = 'STOCK_COUNT' AND status = 'DRAFT';

-- Dòng kiểm kê: SL sổ sách, SL đếm (NULL = chưa đếm), lý do lệch. quantity (cột cũ) = |đếm − sổ sách|
ALTER TABLE stock_document_item
    ADD COLUMN book_quantity    INTEGER,
    ADD COLUMN counted_quantity INTEGER,
    ADD COLUMN reason_id        BIGINT,
    ADD COLUMN reason_note      VARCHAR(200),
    ADD CONSTRAINT ck_stock_document_item_count CHECK (
        (book_quantity IS NULL OR book_quantity >= 0)
        AND (counted_quantity IS NULL OR (book_quantity IS NOT NULL AND counted_quantity >= 0))
        AND (counted_quantity IS NULL OR quantity = abs(counted_quantity - book_quantity))),
    ADD CONSTRAINT fk_stock_document_item_reason FOREIGN KEY (reason_id) REFERENCES stock_count_reason (id);

-- Mã KK + 6 chữ số
CREATE SEQUENCE stock_count_code_seq;
