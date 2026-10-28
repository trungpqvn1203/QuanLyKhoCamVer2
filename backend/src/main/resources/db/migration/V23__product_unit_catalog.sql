-- Migration V23: Đơn vị tính thành danh mục người dùng tự quản lý (BR-PRD-05, BR-UNIT-01..04)
-- Trước đây product.unit chỉ nhận 'BAG' | 'KG' (CHECK). Nay mỗi SP vẫn có MỘT đơn vị, chọn từ bảng product_unit;
-- không quy đổi giữa các đơn vị.

CREATE TABLE product_unit (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                VARCHAR(50)   NOT NULL,
    -- Đơn vị có khối lượng cố định (Kg = 1): SP dùng đơn vị này phải có net_weight_kg đúng bằng giá trị này.
    -- NULL = khối lượng tùy SP (bao 25kg, túi 5kg...). Chỉ dòng hệ thống có giá trị; API không cho đặt
    fixed_net_weight_kg NUMERIC(10,3),
    active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_product_unit_fixed_weight CHECK (fixed_net_weight_kg IS NULL OR fixed_net_weight_kg > 0)
);

-- Tên duy nhất, không phân biệt hoa thường (giống danh mục)
CREATE UNIQUE INDEX uk_product_unit_name ON product_unit (lower(name));

-- Hai đơn vị cũ; nhãn giống giao diện trước đây
INSERT INTO product_unit (name, fixed_net_weight_kg) VALUES ('Bao', NULL), ('Kg', 1);

-- Chuyển dữ liệu: BAG → Bao, KG → Kg
ALTER TABLE product ADD COLUMN unit_id BIGINT;
UPDATE product p SET unit_id = u.id
  FROM product_unit u
 WHERE u.name = CASE p.unit WHEN 'BAG' THEN 'Bao' WHEN 'KG' THEN 'Kg' END;

-- Không có index cho unit_id: chưa màn nào lọc SP theo đơn vị
ALTER TABLE product
    ALTER COLUMN unit_id SET NOT NULL,
    ADD CONSTRAINT fk_product_unit FOREIGN KEY (unit_id) REFERENCES product_unit (id),
    DROP CONSTRAINT ck_product_unit,
    DROP COLUMN unit;
