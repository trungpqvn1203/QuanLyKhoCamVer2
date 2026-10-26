-- Migration V19: sổ quỹ — tiền mặt và tài khoản (BR-CASH-01..07, xác nhận 2026-10-26)
--
-- Sổ quỹ KHÔNG chép tiền vào bảng mới: VIEW cash_flow_entry đọc thẳng từ 4 bảng thanh toán đã có + phiếu thu/chi khác.
-- Một nguồn số liệu duy nhất → sổ quỹ không bao giờ lệch chứng từ, và không phải sửa các module đã duyệt.
-- Đổi sang bảng sổ quỹ ghi kèm từng giao dịch chỉ khi cần chặn quỹ âm hoặc nhiều tài khoản (phải khóa số dư).

-- ===== Đầu kỳ (BR-CASH-03) =====
-- Đúng một dòng (id = 1). Là cấu hình, không phải chứng từ: sửa được, nhưng mọi lần đặt/sửa ghi vào cash_book_change.
CREATE TABLE cash_book (
    id            SMALLINT      PRIMARY KEY,
    start_date    DATE          NOT NULL,
    opening_cash  NUMERIC(18,0) NOT NULL,
    opening_bank  NUMERIC(18,0) NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_cash_book_single_row CHECK (id = 1),
    CONSTRAINT ck_cash_book_opening CHECK (opening_cash >= 0 AND opening_bank >= 0)
);

-- Lịch sử đặt/sửa đầu kỳ — BẤT BIẾN. old_* NULL = lần đặt đầu tiên
CREATE TABLE cash_book_change (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    old_start_date     DATE,
    old_opening_cash   NUMERIC(18,0),
    old_opening_bank   NUMERIC(18,0),
    new_start_date     DATE          NOT NULL,
    new_opening_cash   NUMERIC(18,0) NOT NULL,
    new_opening_bank   NUMERIC(18,0) NOT NULL,
    changed_by         VARCHAR(50)   NOT NULL,
    changed_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- ===== Danh mục thu/chi khác (BR-CASH-05) =====
CREATE TABLE cash_category (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    direction   VARCHAR(3)   NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_cash_category_direction CHECK (direction IN ('IN', 'OUT'))
);

-- Tên duy nhất, không phân biệt hoa thường (như danh mục sản phẩm)
CREATE UNIQUE INDEX uk_cash_category_name ON cash_category (lower(name));

-- ===== Phiếu thu / phiếu chi khác — BẤT BIẾN (BR-CASH-04) =====
CREATE SEQUENCE cash_receipt_code_seq;   -- PT000001
CREATE SEQUENCE cash_payment_code_seq;   -- PC000001

CREATE TABLE cash_voucher (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Phiếu gốc: PT/PC + 6 chữ số; phiếu hoàn: mã gốc + "-H"
    code            VARCHAR(20)   NOT NULL,
    type            VARCHAR(10)   NOT NULL,
    -- Chiều của PHIẾU GỐC (phiếu hoàn giữ nguyên chiều gốc; dấu tiền đảo nhờ type = REVERSAL)
    direction       VARCHAR(3)    NOT NULL,
    account         VARCHAR(4)    NOT NULL,
    category_id     BIGINT        NOT NULL,
    amount          NUMERIC(18,0) NOT NULL,
    voucher_date    DATE          NOT NULL,
    counterparty    VARCHAR(200),
    note            VARCHAR(500),
    reversal_of_id  BIGINT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_cash_voucher_code UNIQUE (code),
    CONSTRAINT ck_cash_voucher_type CHECK (type IN ('VOUCHER', 'REVERSAL')),
    CONSTRAINT ck_cash_voucher_direction CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT ck_cash_voucher_account CHECK (account IN ('CASH', 'BANK')),
    CONSTRAINT ck_cash_voucher_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_voucher_reversal CHECK ((type = 'REVERSAL') = (reversal_of_id IS NOT NULL)),
    CONSTRAINT uk_cash_voucher_reversal_of UNIQUE (reversal_of_id),
    CONSTRAINT fk_cash_voucher_category FOREIGN KEY (category_id) REFERENCES cash_category (id),
    CONSTRAINT fk_cash_voucher_reversal_of FOREIGN KEY (reversal_of_id) REFERENCES cash_voucher (id)
);

CREATE INDEX idx_cash_voucher_date ON cash_voucher (voucher_date);

-- Một hàm chặn dùng chung cho hai bảng bất biến mới; thông báo nêu đúng tên bảng
CREATE FUNCTION prevent_cash_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION '% là bất biến: không được % (BR-CASH-04)', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$;

CREATE TRIGGER trg_cash_voucher_immutable
    BEFORE UPDATE OR DELETE ON cash_voucher
    FOR EACH ROW EXECUTE FUNCTION prevent_cash_change();
CREATE TRIGGER trg_cash_voucher_no_truncate
    BEFORE TRUNCATE ON cash_voucher
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_cash_change();

CREATE TRIGGER trg_cash_book_change_immutable
    BEFORE UPDATE OR DELETE ON cash_book_change
    FOR EACH ROW EXECUTE FUNCTION prevent_cash_change();
CREATE TRIGGER trg_cash_book_change_no_truncate
    BEFORE TRUNCATE ON cash_book_change
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_cash_change();

-- ===== Sổ quỹ: VIEW gộp mọi biến động tiền thật (BR-CASH-02) =====
-- Mỗi dòng: nguồn, id gốc, tài khoản (CASH/BANK), ngày ghi sổ (ngày thu/trả/ứng/lập phiếu), số tiền CÓ DẤU
-- (+ thu vào, − chi ra), chứng từ tham chiếu, đối tượng, danh mục (chỉ phiếu thu/chi), ghi chú,
-- is_reversal (dòng hoàn), reversed (khoản gốc đã bị hoàn), thời điểm ghi.
-- Hình thức: CASH → quỹ tiền mặt, BANK_TRANSFER → tài khoản. Dòng hoàn tính ngược chiều khoản gốc.
CREATE VIEW cash_flow_entry AS
-- Thu tiền khách: thu vào
SELECT 'SALE_PAYMENT'::text                                         AS source,
       p.id                                                         AS source_id,
       CASE p.method WHEN 'CASH' THEN 'CASH' ELSE 'BANK' END        AS account,
       p.payment_date                                               AS entry_date,
       CASE p.type WHEN 'PAYMENT' THEN p.amount ELSE -p.amount END  AS amount,
       d.code::text                                                 AS reference,
       c.name::text                                                 AS counterparty,
       NULL::text                                                   AS category,
       p.note::text                                                 AS note,
       (p.type = 'REVERSAL')                                        AS is_reversal,
       EXISTS (SELECT 1 FROM sale_payment r WHERE r.reversal_of_id = p.id) AS reversed,
       p.created_at                                                 AS created_at
FROM sale_payment p
JOIN stock_document d ON d.id = p.document_id
JOIN customer c ON c.id = d.customer_id
UNION ALL
-- Trả tiền NCC: chi ra
SELECT 'SUPPLIER_PAYMENT', p.id,
       CASE p.method WHEN 'CASH' THEN 'CASH' ELSE 'BANK' END,
       p.payment_date,
       CASE p.type WHEN 'PAYMENT' THEN -p.amount ELSE p.amount END,
       d.code, s.name, NULL, p.note,
       (p.type = 'REVERSAL'),
       EXISTS (SELECT 1 FROM supplier_payment r WHERE r.reversal_of_id = p.id),
       p.created_at
FROM supplier_payment p
JOIN stock_document d ON d.id = p.document_id
JOIN supplier s ON s.id = d.supplier_id
UNION ALL
-- Trả lương: chi ra. BỎ dòng trừ tạm ứng — không có tiền ra khỏi quỹ (tiền đã ra lúc ứng)
SELECT 'PAYROLL_PAYMENT', p.id,
       CASE p.method WHEN 'CASH' THEN 'CASH' ELSE 'BANK' END,
       p.payment_date,
       CASE p.type WHEN 'PAYMENT' THEN -p.amount ELSE p.amount END,
       'Lương ' || to_char(pr.period, 'MM/YYYY'), e.name, NULL, p.note,
       (p.type = 'REVERSAL'),
       EXISTS (SELECT 1 FROM payroll_payment r WHERE r.reversal_of_id = p.id),
       p.created_at
FROM payroll_payment p
JOIN payroll pr ON pr.id = p.payroll_id
JOIN employee e ON e.id = p.employee_id
WHERE p.method <> 'ADVANCE_DEDUCTION'
UNION ALL
-- Tạm ứng nhân viên: chi ra
SELECT 'EMPLOYEE_ADVANCE', a.id,
       CASE a.method WHEN 'CASH' THEN 'CASH' ELSE 'BANK' END,
       a.advance_date,
       CASE a.type WHEN 'ADVANCE' THEN -a.amount ELSE a.amount END,
       'Tạm ứng ' || e.code, e.name, NULL, a.note,
       (a.type = 'REVERSAL'),
       EXISTS (SELECT 1 FROM employee_advance r WHERE r.reversal_of_id = a.id),
       a.created_at
FROM employee_advance a
JOIN employee e ON e.id = a.employee_id
UNION ALL
-- Phiếu thu (+) / phiếu chi (−) khác; phiếu hoàn đảo dấu
SELECT 'CASH_VOUCHER', v.id, v.account, v.voucher_date,
       CASE WHEN (v.direction = 'IN') = (v.type = 'VOUCHER') THEN v.amount ELSE -v.amount END,
       v.code, v.counterparty, cc.name, v.note,
       (v.type = 'REVERSAL'),
       EXISTS (SELECT 1 FROM cash_voucher r WHERE r.reversal_of_id = v.id),
       v.created_at
FROM cash_voucher v
JOIN cash_category cc ON cc.id = v.category_id;
