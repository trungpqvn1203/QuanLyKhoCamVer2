-- Migration V28: Xe và chuyến giao hàng (BR-VEH-01..04, BR-DLV-01..10)
-- Chuyến gom các phiếu bán Nháp theo thứ tự giao. Xuất chuyến = hoàn tất mọi phiếu trong chuyến (trừ tồn) trong MỘT
-- transaction. Không thêm cột nào vào stock_document: phiếu thuộc chuyến nào tra qua delivery_trip_stop.

-- ===== Xe (BR-VEH-01..04) =====
CREATE TABLE vehicle (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Lưu đúng cách người dùng gõ (đã viết hoa) để in ra dễ đọc: "29H-123.45"
    plate_number   VARCHAR(20)   NOT NULL,
    name           VARCHAR(100),
    -- BR-VEH-02: tải trọng để cảnh báo quá tải; NULL = không cảnh báo (xe ba gác, xe máy...)
    capacity_tons  NUMERIC(6,3),
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_vehicle_capacity CHECK (capacity_tons > 0),
    CONSTRAINT ck_vehicle_plate_not_blank CHECK (btrim(plate_number) <> '')
);

-- BR-VEH-01: "29H-123.45", "29h 12345", "29H12345" là cùng một xe → so sánh sau khi bỏ khoảng trắng, chấm, gạch
CREATE UNIQUE INDEX uk_vehicle_plate ON vehicle (upper(regexp_replace(plate_number, '[[:space:].-]', '', 'g')));

-- ===== Chuyến giao (BR-DLV-01..10) =====
CREATE SEQUENCE delivery_trip_code_seq;

CREATE TABLE delivery_trip (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- BR-DLV-01: CG000001, service sinh từ delivery_trip_code_seq (như mã phiếu bán PX)
    code           VARCHAR(20)   NOT NULL,
    trip_date      DATE          NOT NULL,
    vehicle_id     BIGINT        NOT NULL,
    -- Lái xe là một nhân viên (employee); người đi cùng chưa ghi (chấm công để sau)
    driver_id      BIGINT        NOT NULL,
    status         VARCHAR(20)   NOT NULL DEFAULT 'PLANNED',
    note           VARCHAR(500),
    created_by     VARCHAR(50)   NOT NULL,
    dispatched_at  TIMESTAMPTZ,
    dispatched_by  VARCHAR(50),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_delivery_trip_code UNIQUE (code),
    CONSTRAINT ck_delivery_trip_status CHECK (status IN ('PLANNED', 'DISPATCHED')),
    -- BR-DLV-07: Đã xuất thì luôn biết lúc nào, ai xuất; Đang xếp thì chưa có
    CONSTRAINT ck_delivery_trip_dispatched CHECK (
        CASE status
            WHEN 'DISPATCHED' THEN dispatched_at IS NOT NULL AND dispatched_by IS NOT NULL
            ELSE dispatched_at IS NULL AND dispatched_by IS NULL
        END),
    CONSTRAINT fk_delivery_trip_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id),
    CONSTRAINT fk_delivery_trip_driver FOREIGN KEY (driver_id) REFERENCES employee (id)
);

-- Danh sách chuyến lọc theo ngày đi giao
CREATE INDEX idx_delivery_trip_date ON delivery_trip (trip_date);

-- ===== Điểm giao: một phiếu bán trên một chuyến, theo thứ tự =====
CREATE TABLE delivery_trip_stop (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- BR-DLV-09: xóa chuyến Đang xếp → các điểm giao đi theo, phiếu bán trở về "chưa xếp chuyến"
    trip_id      BIGINT   NOT NULL,
    document_id  BIGINT   NOT NULL,
    stop_order   INTEGER  NOT NULL,

    -- BR-DLV-03: một phiếu chỉ nằm trên một chuyến (kể cả chuyến đã xuất — phiếu đã giao rồi không xếp lại)
    CONSTRAINT uk_delivery_trip_stop_document UNIQUE (document_id),
    -- BR-DLV-05: đổi chỗ điểm 1 và 2 thì giữa chừng có hai dòng cùng thứ tự 1 → chỉ kiểm lúc COMMIT
    CONSTRAINT uk_delivery_trip_stop_order UNIQUE (trip_id, stop_order) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_delivery_trip_stop_order CHECK (stop_order > 0),
    CONSTRAINT fk_delivery_trip_stop_trip FOREIGN KEY (trip_id) REFERENCES delivery_trip (id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_trip_stop_document FOREIGN KEY (document_id) REFERENCES stock_document (id)
);
