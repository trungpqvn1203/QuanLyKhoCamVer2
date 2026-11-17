-- Migration V33: đơn giá bán mặc định của sản phẩm (BR-PRD-07, sửa 2026-11-15)
-- Chỉ là giá GỢI Ý điền sẵn khi lập phiếu bán cho khách không có giá riêng (BR-SAL-07).
-- Giá thực bán vẫn chụp trên từng dòng phiếu (BR-SAL-08) → đổi giá ở đây không ảnh hưởng phiếu đã lập.
-- NULL = chưa đặt giá (lập phiếu nhập giá tay). NUMERIC(15,2) như mọi đơn giá (BR-MON-04).
ALTER TABLE product
    ADD COLUMN sale_price NUMERIC(15, 2),
    ADD CONSTRAINT ck_product_sale_price CHECK (sale_price >= 0);

