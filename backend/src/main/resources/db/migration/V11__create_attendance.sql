-- Migration V11: Chấm công theo ngày + tấn bốc vác
-- Quy tắc: BR-ATT-01..02 (docs/business/rules.md)

CREATE TABLE attendance (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id  BIGINT        NOT NULL,
    work_date    DATE          NOT NULL,
    -- BR-ATT-01: FULL = 1 công, HALF = 0,5 công, ABSENT = 0 — phải khớp enum DayStatus
    day_status   VARCHAR(10)   NOT NULL,
    -- BR-ATT-01: tấn bốc vác trong ngày, tối đa 3 chữ số lẻ (cùng độ chính xác với tấn ở báo cáo tồn)
    tons         NUMERIC(8,3)  NOT NULL DEFAULT 0,
    note         VARCHAR(500),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_attendance_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    -- BR-ATT-01: mỗi nhân viên mỗi ngày đúng một dòng
    CONSTRAINT uk_attendance_employee_date UNIQUE (employee_id, work_date),
    CONSTRAINT ck_attendance_status CHECK (day_status IN ('FULL', 'HALF', 'ABSENT')),
    CONSTRAINT ck_attendance_tons CHECK (tons >= 0),
    -- BR-ATT-02: ngày nghỉ thì không có tấn bốc vác
    CONSTRAINT ck_attendance_absent_no_tons CHECK (day_status <> 'ABSENT' OR tons = 0)
);

-- Bảng công tháng đọc theo khoảng ngày (mọi nhân viên). Chiều "một nhân viên" đã có
-- uk_attendance_employee_date (employee_id đứng đầu).
CREATE INDEX idx_attendance_work_date ON attendance (work_date);
