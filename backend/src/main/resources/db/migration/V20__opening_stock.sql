-- Migration V20: Tồn kho đầu kỳ (BR-OPN-01..08, docs/business/rules.md)
-- Phiếu tồn đầu kỳ là một LOẠI CHỨNG TỪ mới trong stock_document, không phải bảng riêng: tồn và lịch sử
-- vẫn đi qua đúng một cơ chế (inventory + stock_transaction), đảo phiếu dùng lại giao dịch đảo sẵn có.

ALTER TABLE stock_document DROP CONSTRAINT ck_stock_document_type;
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_type
    CHECK (type IN ('INBOUND', 'OUTBOUND', 'OPENING_STOCK'));

-- BR-OPN-08: tồn đầu kỳ không có NCC, không có khách, không phát sinh công nợ
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_opening_stock CHECK (
    type <> 'OPENING_STOCK' OR (supplier_id IS NULL AND customer_id IS NULL AND NOT tracks_debt));

-- Người lập / người xác nhận (tên đăng nhập). Hiện chỉ phiếu tồn đầu kỳ ghi; phiếu cũ để NULL
ALTER TABLE stock_document
    ADD COLUMN created_by   VARCHAR(50),
    ADD COLUMN completed_by VARCHAR(50);

-- BR-OPN-01: mã DTK + 6 chữ số
CREATE SEQUENCE opening_stock_code_seq;
