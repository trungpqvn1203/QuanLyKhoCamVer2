-- Migration V27: Tài khoản app khách hàng (ADR-0005, BR-POR-01..10)
-- Vùng đăng nhập RIÊNG với nhân viên (app_user / refresh_token của V14): bảng riêng, FK riêng, thu hồi phiên khách
-- không bao giờ đụng phiên nhân viên. Đơn khách đặt không có bảng mới — là phiếu bán PENDING kênh APP (V25).

CREATE TABLE customer_account (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Đăng nhập bằng SĐT của khách (customer.phone đã duy nhất) → không có cột tên đăng nhập riêng
    customer_id           BIGINT       NOT NULL,
    -- DelegatingPasswordEncoder: "{bcrypt}$2a$10$..." như app_user
    password_hash         VARCHAR(100) NOT NULL,
    -- BR-POR-03: mật khẩu do cửa hàng cấp → khách phải đổi trước khi đặt hàng
    must_change_password  BOOLEAN      NOT NULL DEFAULT TRUE,
    -- BR-POR-09: cửa hàng khóa / mở tài khoản
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    -- BR-POR-04: sai mật khẩu 5 lần liên tiếp → khóa tạm 15 phút
    failed_login_count    INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_by            VARCHAR(50)  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Mỗi khách tối đa một tài khoản; index này cũng phục vụ tra tài khoản theo khách
    CONSTRAINT uk_customer_account_customer UNIQUE (customer_id),
    CONSTRAINT ck_customer_account_failed CHECK (failed_login_count >= 0),
    CONSTRAINT fk_customer_account_customer FOREIGN KEY (customer_id) REFERENCES customer (id)
);

-- Cùng cấu trúc với refresh_token (V14): một lần đăng nhập = một family, mỗi lần gia hạn một dòng mới (rotation).
-- KHÔNG bất biến: được cập nhật replaced_*, revoked_*
CREATE TABLE customer_refresh_token (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_account_id  BIGINT       NOT NULL,
    family_id            UUID         NOT NULL,
    -- SHA-256 (hex) của token; token gốc chỉ nằm trong cookie GW_CRT của trình duyệt
    token_hash           CHAR(64)     NOT NULL,
    -- Hạn "không dùng 30 ngày" của riêng dòng này
    expires_at           TIMESTAMPTZ  NOT NULL,
    -- Hạn tuyệt đối của cả family (90 ngày từ lúc đăng nhập)
    family_expires_at    TIMESTAMPTZ  NOT NULL,
    replaced_by_id       BIGINT,
    replaced_at          TIMESTAMPTZ,
    revoked_at           TIMESTAMPTZ,
    revoke_reason        VARCHAR(30),
    user_agent           VARCHAR(200),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_customer_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_customer_refresh_token_expiry CHECK (expires_at <= family_expires_at),
    CONSTRAINT ck_customer_refresh_token_replaced CHECK ((replaced_by_id IS NULL) = (replaced_at IS NULL)),
    CONSTRAINT ck_customer_refresh_token_revoked CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL)),
    CONSTRAINT ck_customer_refresh_token_reason CHECK (revoke_reason IS NULL
        OR revoke_reason IN ('LOGOUT', 'REUSE_DETECTED', 'PASSWORD_CHANGED', 'ADMIN_ACTION')),
    CONSTRAINT fk_customer_refresh_token_account FOREIGN KEY (customer_account_id) REFERENCES customer_account (id),
    CONSTRAINT fk_customer_refresh_token_replaced_by
        FOREIGN KEY (replaced_by_id) REFERENCES customer_refresh_token (id)
);

-- Thu hồi theo tài khoản (khóa, đặt lại / đổi mật khẩu) và theo family (đăng xuất, phát hiện dùng lại)
CREATE INDEX idx_customer_refresh_token_account ON customer_refresh_token (customer_account_id);
CREATE INDEX idx_customer_refresh_token_family ON customer_refresh_token (family_id);
