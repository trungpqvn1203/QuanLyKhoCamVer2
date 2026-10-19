-- Migration V7: mức tồn tối thiểu của sản phẩm — cảnh báo "Sắp hết" (BR-STK-03)
-- NULL = không theo dõi mức tối thiểu (không bao giờ báo Sắp hết, chỉ báo Hết hàng khi tồn = 0).
-- So với TỔNG tồn mọi kho, kể cả kho đã ngừng hoạt động (BR-STK-01).
ALTER TABLE product
    ADD COLUMN min_stock_quantity INTEGER,
    ADD CONSTRAINT ck_product_min_stock CHECK (min_stock_quantity >= 0);
