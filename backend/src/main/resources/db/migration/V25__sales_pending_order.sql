-- Migration V25: Đơn khách đặt — phiếu bán trạng thái Chờ xác nhận (BR-SAL-16..22, docs/business/rules.md)
-- Đơn khách đặt là phiếu bán (OUTBOUND) ở trạng thái PENDING: chưa có kho, chưa có giá, chưa có dòng phiếu.
-- Phần khách đặt (SP + SL) nằm ở sale_request_item và được giữ nguyên sau khi xác nhận để đối chiếu.

ALTER TABLE stock_document DROP CONSTRAINT ck_stock_document_status;
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_status
    CHECK (status IN ('PENDING', 'DRAFT', 'COMPLETED', 'CANCELLED', 'REVERSED'));

-- BR-SAL-16: chỉ phiếu bán có bước Chờ xác nhận
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_pending CHECK (status <> 'PENDING' OR type = 'OUTBOUND');

ALTER TABLE stock_document
    -- BR-SAL-17: kênh nhận đơn. APP để dành cho app khách
    ADD COLUMN order_channel           VARCHAR(20),
    -- Ngày khách muốn giao — được ở tương lai, khác document_date (ngày bán, BR-SAL-04)
    ADD COLUMN requested_delivery_date DATE,
    -- BR-SAL-19: người và lúc xác nhận (Chờ xác nhận → Nháp)
    ADD COLUMN confirmed_at            TIMESTAMPTZ,
    ADD COLUMN confirmed_by            VARCHAR(50);

-- Phiếu bán đã có đều do kho lập trực tiếp
UPDATE stock_document SET order_channel = 'DIRECT' WHERE type = 'OUTBOUND';

ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_order_channel CHECK (
    (type = 'OUTBOUND') = (order_channel IS NOT NULL)
    AND (order_channel IS NULL OR order_channel IN ('DIRECT', 'PHONE', 'ZALO', 'APP')));
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_delivery_date CHECK (
    type = 'OUTBOUND' OR requested_delivery_date IS NULL);
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_confirmed CHECK (
    (confirmed_at IS NULL) = (confirmed_by IS NULL));

-- Nới V21: ngoài công nợ đầu kỳ, phiếu bán Chờ xác nhận (hoặc bị hủy khi chưa từng xác nhận) được không có kho.
-- Từ Nháp trở đi phiếu bán bắt buộc có kho như cũ (BR-SAL-01)
ALTER TABLE stock_document DROP CONSTRAINT ck_stock_document_warehouse;
ALTER TABLE stock_document ADD CONSTRAINT ck_stock_document_warehouse CHECK (
    CASE
        WHEN type IN ('OPENING_CUSTOMER_DEBT', 'OPENING_SUPPLIER_DEBT') THEN warehouse_id IS NULL
        WHEN type = 'OUTBOUND' AND status IN ('PENDING', 'CANCELLED') AND confirmed_at IS NULL THEN TRUE
        ELSE warehouse_id IS NOT NULL
    END);

-- BR-SAL-17, 20: khách đặt gì. Sửa được khi phiếu PENDING, khóa từ lúc xác nhận (service kiểm tra sau khi khóa phiếu)
CREATE TABLE sale_request_item (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id  BIGINT      NOT NULL,
    product_id   BIGINT      NOT NULL,
    -- Theo đơn vị tính của SP, số nguyên (BR-INV-07)
    quantity     INTEGER     NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Mỗi SP một dòng; index này cũng phục vụ "phần khách đặt của phiếu X"
    CONSTRAINT uk_sale_request_item_product UNIQUE (document_id, product_id),
    CONSTRAINT ck_sale_request_item_quantity CHECK (quantity > 0),
    CONSTRAINT fk_sale_request_item_document FOREIGN KEY (document_id) REFERENCES stock_document (id),
    CONSTRAINT fk_sale_request_item_product FOREIGN KEY (product_id) REFERENCES product (id)
);
