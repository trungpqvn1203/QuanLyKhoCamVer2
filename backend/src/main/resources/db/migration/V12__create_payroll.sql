-- Migration V12: Bảng lương tháng (bản chốt)
-- Quy tắc: BR-PAY-01..03 (docs/business/rules.md)

CREATE TABLE payroll (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Tháng lương, luôn lưu là ngày 1 của tháng (kiểu DATE so sánh/index tốt hơn chuỗi 'YYYY-MM')
    period         DATE          NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    total_amount   NUMERIC(18,0) NOT NULL,
    cancel_reason  VARCHAR(500),
    finalized_at   TIMESTAMPTZ   NOT NULL,
    cancelled_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_payroll_period_first_day CHECK (EXTRACT(DAY FROM period) = 1),
    CONSTRAINT ck_payroll_status CHECK (status IN ('FINALIZED', 'CANCELLED')),
    CONSTRAINT ck_payroll_total CHECK (total_amount >= 0),
    -- BR-PAY-03: hủy chốt phải có lý do và thời điểm; còn hiệu lực thì không có
    CONSTRAINT ck_payroll_cancel CHECK (
        (status = 'CANCELLED' AND cancel_reason IS NOT NULL AND cancelled_at IS NOT NULL)
        OR (status = 'FINALIZED' AND cancel_reason IS NULL AND cancelled_at IS NULL))
);

-- BR-PAY-03: mỗi tháng chỉ MỘT bảng lương còn hiệu lực; các bản đã hủy giữ lại làm lịch sử.
-- Index một phần (partial): chỉ các dòng FINALIZED tham gia ràng buộc duy nhất.
CREATE UNIQUE INDEX uk_payroll_finalized_period ON payroll (period) WHERE status = 'FINALIZED';

CREATE TABLE payroll_item (
    payroll_id    BIGINT        NOT NULL,
    employee_id   BIGINT        NOT NULL,
    work_units    NUMERIC(5,1)  NOT NULL,
    tons          NUMERIC(12,3) NOT NULL,
    -- BR-PAY-02: đơn giá CHỐT lúc tính lương (bản chụp); NULL = không hưởng theo cách đó
    daily_wage    NUMERIC(15,2),
    ton_rate      NUMERIC(15,2),
    -- BR-PAY-01: mỗi phần làm tròn HALF_UP về đồng, tổng = tổng hai phần đã làm tròn
    day_amount    NUMERIC(18,0) NOT NULL,
    ton_amount    NUMERIC(18,0) NOT NULL,
    total_amount  NUMERIC(18,0) NOT NULL,

    CONSTRAINT pk_payroll_item PRIMARY KEY (payroll_id, employee_id),
    CONSTRAINT fk_payroll_item_payroll FOREIGN KEY (payroll_id) REFERENCES payroll (id),
    CONSTRAINT fk_payroll_item_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT ck_payroll_item_quantities CHECK (work_units >= 0 AND tons >= 0),
    CONSTRAINT ck_payroll_item_amounts CHECK (day_amount >= 0 AND ton_amount >= 0),
    CONSTRAINT ck_payroll_item_total CHECK (total_amount = day_amount + ton_amount)
);

-- "Lương của nhân viên X qua các tháng" — khóa chính có employee_id đứng sau nên không dùng được
CREATE INDEX idx_payroll_item_employee ON payroll_item (employee_id);
