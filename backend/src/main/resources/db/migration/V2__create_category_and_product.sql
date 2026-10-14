-- Migration V2: Danh mục sản phẩm và sản phẩm
-- Quy tắc: BR-PRD-01..08, BR-CAT-01..03 (docs/business/rules.md)

CREATE TABLE category (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- BR-CAT-02: tên danh mục duy nhất, không phân biệt hoa thường
CREATE UNIQUE INDEX uk_category_name ON category (lower(name));

-- BR-PRD-02: mã sản phẩm tự sinh, an toàn khi nhiều request đồng thời
CREATE SEQUENCE product_code_seq;

CREATE TABLE product (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(20)   NOT NULL DEFAULT 'SP' || lpad(nextval('product_code_seq')::text, 6, '0'),
    name          VARCHAR(200)  NOT NULL,
    category_id   BIGINT        NOT NULL,
    unit          VARCHAR(20)   NOT NULL,
    net_weight_kg NUMERIC(10,3) NOT NULL,
    description   TEXT,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_product_code       UNIQUE (code),
    CONSTRAINT fk_product_category   FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT ck_product_unit       CHECK (unit IN ('BAG', 'KG')),
    CONSTRAINT ck_product_net_weight CHECK (net_weight_kg > 0)
);

-- Sequence thuộc về cột code: xóa bảng thì sequence bị xóa theo
ALTER SEQUENCE product_code_seq OWNED BY product.code;

-- BR-PRD-03: tên sản phẩm duy nhất, không phân biệt hoa thường
CREATE UNIQUE INDEX uk_product_name ON product (lower(name));

-- Lọc sản phẩm theo danh mục (PostgreSQL không tự tạo index cho FK)
CREATE INDEX idx_product_category ON product (category_id);
