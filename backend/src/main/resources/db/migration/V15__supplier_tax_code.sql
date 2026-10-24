-- Migration V15: nhà cung cấp bỏ "người liên hệ", thêm "mã số thuế" (BR-SUP-01, BR-SUP-09; xác nhận 2026-10-24)
-- Đã xác nhận XÓA HẲN contact_name: dữ liệu người liên hệ đã nhập sẽ mất (không đổi tên cột thành tax_code —
-- "Chị Lan" không phải MST, đổi tên chỉ biến dữ liệu cũ thành dữ liệu sai).

-- NULL = chưa có MST. 14 = 10 số + "-" + 3 số chi nhánh
ALTER TABLE supplier ADD COLUMN tax_code VARCHAR(14);

-- MST Việt Nam: 10 số (doanh nghiệp) hoặc 10 số + "-" + 3 số (chi nhánh), ví dụ 0101234567-001.
-- Service chuẩn hóa (bỏ khoảng trắng) trước khi lưu; CHECK chặn cả đường ghi khác (seed, SQL tay)
ALTER TABLE supplier ADD CONSTRAINT ck_supplier_tax_code
    CHECK (tax_code ~ '^[0-9]{10}(-[0-9]{3})?$');

-- Có nhập thì không trùng NCC khác (kể cả NCC đã ngừng). Index một phần: nhiều NCC để trống vẫn hợp lệ.
-- (UNIQUE thường cũng cho nhiều NULL trong PostgreSQL; viết WHERE để ý đồ rõ ràng và index không chứa dòng NULL)
CREATE UNIQUE INDEX uk_supplier_tax_code ON supplier (tax_code) WHERE tax_code IS NOT NULL;

ALTER TABLE supplier DROP COLUMN contact_name;
