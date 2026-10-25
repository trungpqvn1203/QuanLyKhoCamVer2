-- Migration V18: công nợ nhân viên — trả lương theo bảng lương tháng + tạm ứng (BR-EPAY-01..06, xác nhận 2026-10-26)
--
-- Hai món nợ ngược chiều nhau:
--   * Lương chưa trả: CÔNG TY nợ nhân viên  = payroll_item.total_amount − payroll_item.paid_amount
--   * Tạm ứng:        NHÂN VIÊN nợ công ty  = employee.advance_balance (trừ dần vào lương, không trả bằng tiền mặt)
-- Mỗi món có một nhật ký BẤT BIẾN (trigger chặn sửa/xóa) và một số tổng (snapshot) có CHECK ở DB,
-- giống inventory ↔ stock_transaction.

-- ===== Bảng lương cũ coi như đã trả đủ (BR-EPAY-05) =====
-- Cờ ở bảng lương (mỗi tháng một bản): bảng lương đã chốt trước module này không phát sinh nợ.
-- Bảng lương chốt từ nay về sau: DEFAULT TRUE. Hủy chốt rồi chốt lại một tháng cũ thì bản mới phát sinh nợ bình thường.
ALTER TABLE payroll ADD COLUMN tracks_debt BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE payroll SET tracks_debt = FALSE WHERE status = 'FINALIZED';

-- ===== Số đã trả của từng dòng lương =====
-- Snapshot của payroll_payment: gồm cả phần trừ tạm ứng (trừ tạm ứng cũng là một cách tất toán lương).
-- Không bao giờ trả quá số lương của dòng (BR-EPAY-02) — tuyến phòng thủ cuối kể cả khi code quên kiểm tra
ALTER TABLE payroll_item ADD COLUMN paid_amount NUMERIC(18,0) NOT NULL DEFAULT 0;
ALTER TABLE payroll_item ADD CONSTRAINT ck_payroll_item_paid CHECK (paid_amount >= 0 AND paid_amount <= total_amount);

-- ===== Số dư tạm ứng của nhân viên =====
-- Không bao giờ âm (BR-EPAY-03): cùng tinh thần "không tồn kho âm" — kiểm tra + trừ trong một transaction,
-- khóa dòng nhân viên FOR UPDATE, và CHECK ở đây là chốt chặn cuối.
ALTER TABLE employee ADD COLUMN advance_balance NUMERIC(18,0) NOT NULL DEFAULT 0;
ALTER TABLE employee ADD CONSTRAINT ck_employee_advance_balance CHECK (advance_balance >= 0);

-- Màn công nợ chỉ liệt kê nhân viên còn dư tạm ứng
CREATE INDEX idx_employee_advance_balance ON employee (advance_balance) WHERE advance_balance > 0;

-- ===== Nhật ký tạm ứng — BẤT BIẾN (BR-EPAY-03, 04) =====
CREATE TABLE employee_advance (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id     BIGINT        NOT NULL,
    type            VARCHAR(10)   NOT NULL,
    -- Tiền công ty chi ra: tiền mặt hoặc chuyển khoản. Ứng kết hợp = hai dòng
    method          VARCHAR(20)   NOT NULL,
    amount          NUMERIC(18,0) NOT NULL,
    advance_date    DATE          NOT NULL,
    note            VARCHAR(500),
    -- Dòng hoàn trỏ về khoản ứng gốc (ghi nhầm)
    reversal_of_id  BIGINT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_employee_advance_type CHECK (type IN ('ADVANCE', 'REVERSAL')),
    CONSTRAINT ck_employee_advance_method CHECK (method IN ('CASH', 'BANK_TRANSFER')),
    CONSTRAINT ck_employee_advance_amount CHECK (amount > 0),
    CONSTRAINT ck_employee_advance_reversal CHECK ((type = 'REVERSAL') = (reversal_of_id IS NOT NULL)),
    -- Mỗi khoản ứng chỉ được hoàn một lần
    CONSTRAINT uk_employee_advance_reversal_of UNIQUE (reversal_of_id),
    CONSTRAINT fk_employee_advance_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_employee_advance_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES employee_advance (id)
);

CREATE INDEX idx_employee_advance_employee ON employee_advance (employee_id);

CREATE FUNCTION prevent_employee_advance_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'employee_advance là bất biến: không được % (BR-EPAY-04)', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_employee_advance_immutable
    BEFORE UPDATE OR DELETE ON employee_advance
    FOR EACH ROW EXECUTE FUNCTION prevent_employee_advance_change();

CREATE TRIGGER trg_employee_advance_no_truncate
    BEFORE TRUNCATE ON employee_advance
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_employee_advance_change();

-- ===== Nhật ký trả lương — BẤT BIẾN (BR-EPAY-02, 04) =====
CREATE TABLE payroll_payment (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payroll_id      BIGINT        NOT NULL,
    employee_id     BIGINT        NOT NULL,
    type            VARCHAR(10)   NOT NULL,
    -- ADVANCE_DEDUCTION: trừ vào tạm ứng — KHÔNG có tiền ra khỏi quỹ, chỉ giảm nợ hai chiều
    method          VARCHAR(20)   NOT NULL,
    amount          NUMERIC(18,0) NOT NULL,
    payment_date    DATE          NOT NULL,
    note            VARCHAR(500),
    reversal_of_id  BIGINT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_payroll_payment_type CHECK (type IN ('PAYMENT', 'REVERSAL')),
    CONSTRAINT ck_payroll_payment_method CHECK (method IN ('CASH', 'BANK_TRANSFER', 'ADVANCE_DEDUCTION')),
    CONSTRAINT ck_payroll_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payroll_payment_reversal CHECK ((type = 'REVERSAL') = (reversal_of_id IS NOT NULL)),
    CONSTRAINT uk_payroll_payment_reversal_of UNIQUE (reversal_of_id),
    -- Khóa ngoại kép tới dòng lương: khoản trả luôn gắn với đúng (bảng lương, nhân viên)
    CONSTRAINT fk_payroll_payment_item FOREIGN KEY (payroll_id, employee_id) REFERENCES payroll_item (payroll_id, employee_id),
    CONSTRAINT fk_payroll_payment_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES payroll_payment (id)
);

CREATE INDEX idx_payroll_payment_item ON payroll_payment (payroll_id, employee_id);

CREATE FUNCTION prevent_payroll_payment_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'payroll_payment là bất biến: không được % (BR-EPAY-04)', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_payroll_payment_immutable
    BEFORE UPDATE OR DELETE ON payroll_payment
    FOR EACH ROW EXECUTE FUNCTION prevent_payroll_payment_change();

CREATE TRIGGER trg_payroll_payment_no_truncate
    BEFORE TRUNCATE ON payroll_payment
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_payroll_payment_change();
