-- Migration V3: Kho hàng
-- Quy tắc: BR-WH-01..05 (docs/business/rules.md)

-- BR-WH-02: mã kho tự sinh, an toàn khi nhiều request đồng thời
CREATE SEQUENCE warehouse_code_seq;

CREATE TABLE warehouse (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(20)  NOT NULL DEFAULT 'KHO' || lpad(nextval('warehouse_code_seq')::text, 3, '0'),
    name        VARCHAR(200) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    address     VARCHAR(500) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_warehouse_code UNIQUE (code),
    CONSTRAINT ck_warehouse_type CHECK (type IN ('CENTRAL', 'RETAIL'))
);

-- Sequence thuộc về cột code: xóa bảng thì sequence bị xóa theo
ALTER SEQUENCE warehouse_code_seq OWNED BY warehouse.code;

-- BR-WH-03: tên kho duy nhất, không phân biệt hoa thường
CREATE UNIQUE INDEX uk_warehouse_name ON warehouse (lower(name));
