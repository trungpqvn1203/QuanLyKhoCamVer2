-- =============================================
-- Dữ liệu demo — CHỈ dùng cho DB dev, không chạy trên test/production
-- =============================================
-- Yêu cầu: DB đã migrate tới V3 (chạy backend một lần để Flyway tạo bảng).
-- Cách chạy (từ thư mục gốc dự án):
--   psql -h localhost -U postgres -d grain_warehouse -f db/seed/demo-data.sql
--
-- Idempotent: chạy lại nhiều lần không lỗi, không nhân đôi dữ liệu
-- (ON CONFLICT dựa trên unique index lower(name)).
-- Không ghi id/code: DB tự sinh (BR-PRD-02, BR-WH-02).

\set ON_ERROR_STOP on

BEGIN;

-- ---------- Danh mục ----------
INSERT INTO category (name, description) VALUES
    ('Cám heo', 'Thức ăn hỗn hợp cho heo các giai đoạn'),
    ('Cám gà',  'Thức ăn cho gà thịt và gà đẻ'),
    ('Cám vịt', 'Thức ăn cho vịt thịt và vịt đẻ'),
    ('Cám cá',  'Thức ăn viên nổi cho cá')
ON CONFLICT ((lower(name))) DO NOTHING;

-- ---------- Sản phẩm ----------
-- Lấy category_id theo tên để không phụ thuộc id tự sinh
INSERT INTO product (name, category_id, unit, net_weight_kg, description, active)
SELECT p.name, c.id, p.unit, p.net_weight_kg, p.description, p.active
FROM (VALUES
    ('Cám heo con tập ăn 25kg',  'Cám heo', 'BAG', 25.000, 'Heo con 7–15 kg',            TRUE),
    ('Cám heo thịt 40kg',        'Cám heo', 'BAG', 40.000, 'Heo thịt 30–100 kg',         TRUE),
    ('Cám heo nái nuôi con 25kg','Cám heo', 'BAG', 25.000, NULL,                         TRUE),
    ('Cám gà thịt 25kg',         'Cám gà',  'BAG', 25.000, 'Gà thịt 1–21 ngày tuổi',     TRUE),
    ('Cám gà đẻ 40kg',           'Cám gà',  'BAG', 40.000, NULL,                         TRUE),
    ('Cám vịt đẻ 25kg',          'Cám vịt', 'BAG', 25.000, NULL,                         TRUE),
    ('Cám cá viên nổi xá',       'Cám cá',  'KG',   1.000, 'Bán lẻ theo kg',             TRUE),
    -- Sản phẩm đã ngừng kinh doanh: để demo trạng thái active=false trên giao diện
    ('Cám vịt con 20kg',         'Cám vịt', 'BAG', 20.000, 'Ngừng sản xuất từ 2026',     FALSE)
) AS p(name, category_name, unit, net_weight_kg, description, active)
JOIN category c ON lower(c.name) = lower(p.category_name)
ON CONFLICT ((lower(name))) DO NOTHING;

-- ---------- Kho ----------
INSERT INTO warehouse (name, type, address, active) VALUES
    ('Kho tổng Hà Nội',        'CENTRAL', 'KCN Phú Nghĩa, Chương Mỹ, Hà Nội',        TRUE),
    ('Kho tổng Hưng Yên',      'CENTRAL', 'KCN Phố Nối A, Văn Lâm, Hưng Yên',        TRUE),
    ('Cửa hàng Bắc Ninh',      'RETAIL',  '25 Nguyễn Gia Thiều, TP Bắc Ninh',        TRUE),
    ('Cửa hàng Thái Bình',     'RETAIL',  '112 Lý Bôn, TP Thái Bình',                TRUE),
    -- Kho đã ngừng hoạt động: để demo trạng thái active=false trên giao diện
    ('Cửa hàng Hải Dương cũ',  'RETAIL',  '8 Trần Hưng Đạo, TP Hải Dương',           FALSE)
ON CONFLICT ((lower(name))) DO NOTHING;

COMMIT;

-- Tóm tắt sau khi seed
SELECT 'category' AS bang, count(*) AS so_dong FROM category
UNION ALL SELECT 'product', count(*) FROM product
UNION ALL SELECT 'warehouse', count(*) FROM warehouse;
