-- Migration V21: Công nợ đầu kỳ khách hàng / NCC (BR-ODB-01..09, docs/business/rules.md)
-- Mỗi khoản nợ cũ là một chứng từ KHÔNG có dòng hàng trong stock_document, để thu/trả, hoàn khoản thu nhầm,
-- trang công nợ và sổ quỹ dùng lại nguyên cơ chế của phiếu bán (sale_payment) / phiếu nhập (supplier_payment).

-- 'OPENING_CUSTOMER_DEBT' dài 21 ký tự, cột cũ VARCHAR(20). Nới cột chỉ đổi giới hạn, không viết lại dữ liệu
ALTER TABLE stock_document ALTER COLUMN type TYPE VARCHAR(30);

-- Chứng từ nợ đầu kỳ không thuộc kho nào. Chứng từ kho vẫn bắt buộc có kho nhờ CHECK bên dưới
ALTER TABLE stock_document ALTER COLUMN warehouse_id DROP NOT NULL;

ALTER TABLE stock_document DROP CONSTRAINT ck_stock_document_type;
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_type CHECK (type IN (
    'INBOUND', 'OUTBOUND', 'OPENING_STOCK', 'OPENING_CUSTOMER_DEBT', 'OPENING_SUPPLIER_DEBT'));

ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_warehouse CHECK (
    (type IN ('OPENING_CUSTOMER_DEBT', 'OPENING_SUPPLIER_DEBT')) = (warehouse_id IS NULL));

-- BR-ODB-01: nợ khách có khách, không có NCC; nợ NCC ngược lại. Luôn theo dõi công nợ
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_opening_debt CHECK (
    (type <> 'OPENING_CUSTOMER_DEBT' OR (customer_id IS NOT NULL AND supplier_id IS NULL AND tracks_debt))
    AND (type <> 'OPENING_SUPPLIER_DEBT' OR (supplier_id IS NOT NULL AND customer_id IS NULL AND tracks_debt)));

-- BR-ODB-02: số nợ > 0 (chứng từ khác cho phép 0 — phiếu toàn hàng tặng)
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_opening_debt_amount CHECK (
    type NOT IN ('OPENING_CUSTOMER_DEBT', 'OPENING_SUPPLIER_DEBT') OR total_amount > 0);

-- BR-ODB-03: mỗi khách / NCC chỉ MỘT khoản nợ đầu kỳ còn hiệu lực (Đã đảo thì được nhập lại).
-- Chặn ở DB: hai người cùng nhập nợ đầu kỳ cho một khách cùng lúc → người sau vi phạm UNIQUE
CREATE UNIQUE INDEX uk_stock_document_opening_customer_debt ON stock_document (customer_id)
    WHERE type = 'OPENING_CUSTOMER_DEBT' AND status = 'COMPLETED';
CREATE UNIQUE INDEX uk_stock_document_opening_supplier_debt ON stock_document (supplier_id)
    WHERE type = 'OPENING_SUPPLIER_DEBT' AND status = 'COMPLETED';

-- Mã DKH000001 (khách), DNC000001 (NCC)
CREATE SEQUENCE opening_customer_debt_code_seq;
CREATE SEQUENCE opening_supplier_debt_code_seq;
