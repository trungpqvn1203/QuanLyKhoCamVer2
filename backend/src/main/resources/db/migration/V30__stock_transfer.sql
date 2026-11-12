-- Migration V30: Điều chuyển kho (BR-TRF-01..12, docs/business/rules.md)
-- Phiếu điều chuyển là một LOẠI CHỨNG TỪ (TRANSFER) trong stock_document, như tồn đầu kỳ (V20) và kiểm kê (V22):
-- warehouse_id = kho NGUỒN, target_warehouse_id = kho ĐÍCH. Mỗi dòng phiếu sinh HAI stock_transaction cùng trỏ về
-- dòng đó: OUT tại kho nguồn và IN tại kho đích — tồn và lịch sử vẫn đi qua đúng một cơ chế (InventoryService).

ALTER TABLE stock_document DROP CONSTRAINT ck_stock_document_type;
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_type CHECK (type IN (
    'INBOUND', 'OUTBOUND', 'OPENING_STOCK', 'OPENING_CUSTOMER_DEBT', 'OPENING_SUPPLIER_DEBT', 'STOCK_COUNT',
    'TRANSFER'));

ALTER TABLE stock_document ADD COLUMN target_warehouse_id BIGINT;

ALTER TABLE stock_document
    ADD CONSTRAINT fk_stock_document_target_warehouse FOREIGN KEY (target_warehouse_id) REFERENCES warehouse (id),
    -- Có kho đích khi và chỉ khi là phiếu điều chuyển
    ADD CONSTRAINT ck_stock_document_target_warehouse CHECK ((type = 'TRANSFER') = (target_warehouse_id IS NOT NULL)),
    -- BR-TRF-05: không chuyển từ một kho sang chính nó (target NULL → biểu thức NULL → CHECK coi là đạt)
    ADD CONSTRAINT ck_stock_document_transfer_distinct CHECK (target_warehouse_id <> warehouse_id),
    -- BR-TRF-06: hàng chỉ đổi chỗ trong hệ thống — không NCC, không khách, không công nợ
    ADD CONSTRAINT ck_stock_document_transfer CHECK (
        type <> 'TRANSFER' OR (supplier_id IS NULL AND customer_id IS NULL AND NOT tracks_debt));

-- BR-TRF-07: mã DC + 6 chữ số (PC đã là phiếu chi của sổ quỹ)
CREATE SEQUENCE transfer_code_seq;
