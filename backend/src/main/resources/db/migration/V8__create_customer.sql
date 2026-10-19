-- Migration V8: Khách hàng
-- Quy tắc: BR-CUS-01..06 (docs/business/rules.md)

-- BR-CUS-02: mã KH tự sinh dạng KH0001, an toàn khi nhiều request đồng thời.
-- Quá 9999 thì lpad không cắt mà mã dài thêm (KH10000) — vẫn duy nhất.
CREATE SEQUENCE customer_code_seq;

CREATE TABLE customer (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(20)   NOT NULL DEFAULT 'KH' || lpad(nextval('customer_code_seq')::text, 4, '0'),
    name          VARCHAR(200)  NOT NULL,
    type          VARCHAR(20)   NOT NULL,
    phone         VARCHAR(20)   NOT NULL,
    contact_name  VARCHAR(200),
    address       VARCHAR(500),
    note          VARCHAR(1000),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_customer_code UNIQUE (code),
    -- BR-CUS-04: SĐT là định danh chống trùng (tên được phép trùng), tính cả khách đã ngừng (BR-CUS-05)
    CONSTRAINT uk_customer_phone UNIQUE (phone),
    -- BR-CUS-03: phải khớp enum CustomerType
    CONSTRAINT ck_customer_type CHECK (type IN ('DEALER', 'FARM', 'RETAIL')),
    -- BR-CUS-06: chỉ lưu dạng đã chuẩn hóa — nếu cho lưu "0901 234 567" thì UNIQUE ở trên để lọt trùng
    CONSTRAINT ck_customer_phone CHECK (phone ~ '^0[0-9]{9,10}$')
);

ALTER SEQUENCE customer_code_seq OWNED BY customer.code;
