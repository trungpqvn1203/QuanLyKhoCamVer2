-- Migration V9: Nhân viên (chấm công V10, bảng lương V11)
-- Quy tắc: BR-EMP-01..03 (docs/business/rules.md)

-- BR-EMP-01: mã NV tự sinh dạng NV001
CREATE SEQUENCE employee_code_seq;

CREATE TABLE employee (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(20)   NOT NULL DEFAULT 'NV' || lpad(nextval('employee_code_seq')::text, 3, '0'),
    name        VARCHAR(200)  NOT NULL,
    phone       VARCHAR(20),
    -- BR-EMP-02, BR-MON-04: đơn giá 2 chữ số lẻ; NULL = không hưởng theo cách đó
    daily_wage  NUMERIC(15,2),
    ton_rate    NUMERIC(15,2),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_employee_code UNIQUE (code),
    -- BR-EMP-02: phải hưởng lương theo ít nhất một cách
    CONSTRAINT ck_employee_has_rate CHECK (daily_wage IS NOT NULL OR ton_rate IS NOT NULL),
    -- So sánh với NULL cho ra NULL → CHECK coi là đạt, nên cột để trống vẫn hợp lệ
    CONSTRAINT ck_employee_rates CHECK (daily_wage >= 0 AND ton_rate >= 0),
    -- BR-EMP-03: tùy chọn, không duy nhất, nhưng có nhập thì phải ở dạng đã chuẩn hóa (như BR-CUS-06)
    CONSTRAINT ck_employee_phone CHECK (phone ~ '^0[0-9]{9,10}$')
);

ALTER SEQUENCE employee_code_seq OWNED BY employee.code;
