-- Migration V24: Thông tin cửa hàng in trên phiếu (BR-PRT-01)
-- Đúng một dòng (id = 1) như cash_book. Không tạo sẵn: chưa cấu hình thì phiếu in vẫn chạy, chỉ thiếu phần đầu.

CREATE TABLE store_info (
    id             SMALLINT     PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    address        VARCHAR(500),
    -- Tự do (có thể ghi hai số "0912 345 678 - 0987 654 321"), chỉ để in
    phone          VARCHAR(50),
    -- Dòng cuối phiếu, ví dụ "Cảm ơn quý khách, hẹn gặp lại!"
    receipt_footer VARCHAR(300),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_store_info_single_row CHECK (id = 1)
);
