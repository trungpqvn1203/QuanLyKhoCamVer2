-- Migration V34: nhiều bảng giá bán có tên (BR-PRC-01..07) — ví dụ "Giá lẻ", "Giá đại lý", "Giá trang trại".
-- Mỗi bảng giá có một giá hiện hành cho mỗi SP; khách được gán (tùy chọn) một bảng giá.
-- Chỉ là giá GỢI Ý điền sẵn khi lập phiếu bán (BR-SAL-07); giá thực bán vẫn chụp trên dòng phiếu (BR-SAL-08).

CREATE TABLE price_list (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- BR-PRC-01: tên bảng giá duy nhất, không phân biệt hoa thường (như danh mục)
CREATE UNIQUE INDEX uk_price_list_name ON price_list (lower(name));

CREATE TABLE price_list_item (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    price_list_id BIGINT        NOT NULL,
    product_id    BIGINT        NOT NULL,
    unit_price    NUMERIC(15,2) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Mỗi SP một giá trong một bảng giá; index này cũng phục vụ "giá của bảng X" (price_list_id đứng đầu)
    CONSTRAINT uk_price_list_item UNIQUE (price_list_id, product_id),
    CONSTRAINT ck_price_list_item_price CHECK (unit_price >= 0),
    CONSTRAINT fk_price_list_item_list FOREIGN KEY (price_list_id) REFERENCES price_list (id),
    CONSTRAINT fk_price_list_item_product FOREIGN KEY (product_id) REFERENCES product (id)
);

-- Chiều ngược "giá của SP Y trong mọi bảng giá" (màn Bảng giá bán dạng ma trận)
CREATE INDEX idx_price_list_item_product ON price_list_item (product_id);

-- BR-PRC-04: bảng giá áp dụng cho khách (tùy chọn)
ALTER TABLE customer
    ADD COLUMN price_list_id BIGINT,
    ADD CONSTRAINT fk_customer_price_list FOREIGN KEY (price_list_id) REFERENCES price_list (id);
