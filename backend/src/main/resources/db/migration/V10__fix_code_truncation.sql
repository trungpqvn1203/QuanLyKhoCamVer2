-- Migration V10: sửa mã tự sinh bị CẮT CỤT khi số thứ tự dài hơn độ rộng đệm
--
-- Lỗi: lpad(text, n) không chỉ đệm mà còn CẮT chuỗi dài hơn n ký tự (giữ n ký tự đầu):
--   'KHO' || lpad('1050', 3, '0')  →  'KHO105'   (không phải 'KHO1050')
--   'KHO' || lpad('1051', 3, '0')  →  'KHO105'   → trùng uk_warehouse_code → lỗi 500
-- Ảnh hưởng mọi bảng sinh mã bằng lpad: product (V2), warehouse (V3), supplier (V4), customer (V8), employee (V9).
-- V8 từng ghi "quá 9999 thì mã dài thêm" — SAI, đã kiểm chứng bằng psql.
-- Không sửa migration cũ: Flyway lưu checksum, sửa file đã chạy thì validate thất bại trên mọi DB đã có.
-- Mã đã sinh giữ nguyên; chỉ đổi cách sinh mã từ nay về sau.

-- Đệm số 0 khi số ngắn hơn width, giữ nguyên khi dài hơn: (KHO, 7, 3) → KHO007; (KHO, 1050, 3) → KHO1050
CREATE OR REPLACE FUNCTION format_code(prefix TEXT, seq_value BIGINT, width INTEGER)
    RETURNS TEXT
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    STRICT
AS $$
    SELECT prefix || CASE
        WHEN length(seq_value::text) >= width THEN seq_value::text
        ELSE lpad(seq_value::text, width, '0')
    END
$$;

-- nextval chỉ gọi một lần mỗi dòng (truyền vào hàm), nên vẫn an toàn khi nhiều request đồng thời như trước
ALTER TABLE product   ALTER COLUMN code SET DEFAULT format_code('SP',  nextval('product_code_seq'),   6);
ALTER TABLE warehouse ALTER COLUMN code SET DEFAULT format_code('KHO', nextval('warehouse_code_seq'), 3);
ALTER TABLE supplier  ALTER COLUMN code SET DEFAULT format_code('NCC', nextval('supplier_code_seq'),  3);
ALTER TABLE customer  ALTER COLUMN code SET DEFAULT format_code('KH',  nextval('customer_code_seq'),  4);
ALTER TABLE employee  ALTER COLUMN code SET DEFAULT format_code('NV',  nextval('employee_code_seq'),  3);
