-- Migration V17: tách tấn "bốc xuống" và tấn "giao hàng" (BR-ATT-03, BR-EMP-04, BR-PAY-04; xác nhận 2026-10-26)
--
-- Hai loại tấn có đơn giá khác nhau. Tấn cũ (tons / ton_rate / ton_amount) đã được xác nhận là tấn BỐC XUỐNG,
-- nên đổi tên cột (RENAME) để giữ nguyên dữ liệu và bản chụp của các bảng lương đã chốt; phần GIAO HÀNG của dữ
-- liệu cũ là 0 / NULL. RENAME chỉ đúng khi ý nghĩa giữ nguyên — ở đây tấn cũ vốn là bốc xuống nên giữ nguyên ý nghĩa.

-- ===== employee: hai đơn giá tấn =====
ALTER TABLE employee RENAME COLUMN ton_rate TO unload_ton_rate;
ALTER TABLE employee ADD COLUMN delivery_ton_rate NUMERIC(15,2);

ALTER TABLE employee DROP CONSTRAINT ck_employee_has_rate;
ALTER TABLE employee DROP CONSTRAINT ck_employee_rates;
-- BR-EMP-02 (mở rộng): phải hưởng lương theo ít nhất một cách trong ba cách
ALTER TABLE employee ADD CONSTRAINT ck_employee_has_rate
    CHECK (daily_wage IS NOT NULL OR unload_ton_rate IS NOT NULL OR delivery_ton_rate IS NOT NULL);
-- So sánh với NULL cho ra NULL → CHECK coi là đạt, nên cột để trống vẫn hợp lệ
ALTER TABLE employee ADD CONSTRAINT ck_employee_rates
    CHECK (daily_wage >= 0 AND unload_ton_rate >= 0 AND delivery_ton_rate >= 0);

-- ===== attendance: hai loại tấn mỗi ngày =====
ALTER TABLE attendance RENAME COLUMN tons TO unload_tons;
ALTER TABLE attendance ADD COLUMN delivery_tons NUMERIC(8,3) NOT NULL DEFAULT 0;

ALTER TABLE attendance DROP CONSTRAINT ck_attendance_tons;
ALTER TABLE attendance DROP CONSTRAINT ck_attendance_absent_no_tons;
ALTER TABLE attendance ADD CONSTRAINT ck_attendance_tons CHECK (unload_tons >= 0 AND delivery_tons >= 0);
-- BR-ATT-02: ngày nghỉ thì cả hai loại tấn đều bằng 0
ALTER TABLE attendance ADD CONSTRAINT ck_attendance_absent_no_tons
    CHECK (day_status <> 'ABSENT' OR (unload_tons = 0 AND delivery_tons = 0));

-- ===== payroll_item: bản chụp lương có hai phần tấn =====
ALTER TABLE payroll_item RENAME COLUMN tons TO unload_tons;
ALTER TABLE payroll_item RENAME COLUMN ton_rate TO unload_ton_rate;
ALTER TABLE payroll_item RENAME COLUMN ton_amount TO unload_amount;
-- Dòng lương đã chốt trước đây không có phần giao hàng: số lượng và tiền = 0, đơn giá = NULL (không hưởng)
ALTER TABLE payroll_item ADD COLUMN delivery_tons NUMERIC(12,3) NOT NULL DEFAULT 0;
ALTER TABLE payroll_item ADD COLUMN delivery_ton_rate NUMERIC(15,2);
ALTER TABLE payroll_item ADD COLUMN delivery_amount NUMERIC(18,0) NOT NULL DEFAULT 0;
-- Dòng lương mới bắt buộc ghi rõ cả hai phần, không được quên một phần mà vẫn lưu được
ALTER TABLE payroll_item ALTER COLUMN delivery_tons DROP DEFAULT;
ALTER TABLE payroll_item ALTER COLUMN delivery_amount DROP DEFAULT;

ALTER TABLE payroll_item DROP CONSTRAINT ck_payroll_item_quantities;
ALTER TABLE payroll_item DROP CONSTRAINT ck_payroll_item_amounts;
ALTER TABLE payroll_item DROP CONSTRAINT ck_payroll_item_total;
ALTER TABLE payroll_item ADD CONSTRAINT ck_payroll_item_quantities
    CHECK (work_units >= 0 AND unload_tons >= 0 AND delivery_tons >= 0);
ALTER TABLE payroll_item ADD CONSTRAINT ck_payroll_item_amounts
    CHECK (day_amount >= 0 AND unload_amount >= 0 AND delivery_amount >= 0);
-- BR-PAY-01: tổng = tổng ba phần đã làm tròn
ALTER TABLE payroll_item ADD CONSTRAINT ck_payroll_item_total
    CHECK (total_amount = day_amount + unload_amount + delivery_amount);
