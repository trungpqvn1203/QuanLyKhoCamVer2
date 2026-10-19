-- Migration V4: Nhà cung cấp + liên kết nhà cung cấp – sản phẩm
-- Quy tắc: BR-SUP-01..07 (docs/business/rules.md)

-- BR-SUP-02: mã NCC tự sinh, an toàn khi nhiều request đồng thời
CREATE SEQUENCE supplier_code_seq;

CREATE TABLE supplier (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(20)  NOT NULL DEFAULT 'NCC' || lpad(nextval('supplier_code_seq')::text, 3, '0'),
    name          VARCHAR(200) NOT NULL,
    contact_name  VARCHAR(200) NOT NULL,
    phone         VARCHAR(20),
    email         VARCHAR(254),
    address       VARCHAR(500),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_supplier_code UNIQUE (code)
);

-- Sequence thuộc về cột code: xóa bảng thì sequence bị xóa theo
ALTER SEQUENCE supplier_code_seq OWNED BY supplier.code;

-- BR-SUP-03: tên NCC duy nhất, không phân biệt hoa thường
CREATE UNIQUE INDEX uk_supplier_name ON supplier (lower(name));

-- BR-SUP-05: NCC được phép cung cấp SP nào.
-- Khóa chính kép: mỗi cặp (NCC, SP) chỉ có đúng một dòng; gỡ liên kết = active=false,
-- gán lại = bật active=true trên chính dòng đó.
CREATE TABLE product_supplier (
    supplier_id  BIGINT      NOT NULL,
    product_id   BIGINT      NOT NULL,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_product_supplier PRIMARY KEY (supplier_id, product_id),
    CONSTRAINT fk_product_supplier_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_product_supplier_product FOREIGN KEY (product_id) REFERENCES product (id)
);

-- Khóa chính đã phục vụ "NCC X cung cấp SP nào"; index này phục vụ chiều ngược lại
-- "SP Y do những NCC nào cung cấp" (product_id đứng sau trong khóa chính nên không dùng được)
CREATE INDEX idx_product_supplier_product ON product_supplier (product_id);
