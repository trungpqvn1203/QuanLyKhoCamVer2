-- Migration V14: Người dùng, vai trò, refresh token (ADR-0003)

CREATE TABLE app_user (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL,
    -- DelegatingPasswordEncoder: "{bcrypt}$2a$10$..." — tiền tố cho biết thuật toán, đổi thuật toán sau không phải migrate
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(100) NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Tên đăng nhập chỉ gồm chữ thường, số, dấu chấm, gạch dưới, gạch ngang — service chuẩn hóa về chữ thường trước khi lưu
    CONSTRAINT ck_app_user_username CHECK (username ~ '^[a-z0-9._-]{3,50}$')
);

CREATE UNIQUE INDEX uk_app_user_username ON app_user (username);

-- Một người có thể kiêm nhiều vai trò; quyền = hợp quyền các vai trò. Khóa chính kép: mỗi vai trò một dòng
CREATE TABLE app_user_role (
    user_id  BIGINT      NOT NULL,
    role     VARCHAR(30) NOT NULL,

    CONSTRAINT pk_app_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT ck_app_user_role CHECK (role IN ('WAREHOUSE_MANAGER', 'SALES_STAFF', 'SYSTEM_ADMIN')),
    CONSTRAINT fk_app_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

-- Một lần đăng nhập = một family; mỗi lần gia hạn thêm một dòng mới, dòng cũ trỏ sang dòng mới (rotation).
-- KHÔNG bất biến như stock_transaction: được cập nhật replaced_*, revoked_*.
CREATE TABLE refresh_token (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    family_id          UUID         NOT NULL,
    -- SHA-256 (hex) của token; token gốc chỉ nằm trong cookie của trình duyệt
    token_hash         CHAR(64)     NOT NULL,
    -- Hạn "không thao tác 8 giờ" của riêng dòng này
    expires_at         TIMESTAMPTZ  NOT NULL,
    -- Hạn tuyệt đối của cả family (24 giờ từ lúc đăng nhập), chép sang mọi dòng của family
    family_expires_at  TIMESTAMPTZ  NOT NULL,
    replaced_by_id     BIGINT,
    replaced_at        TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    revoke_reason      VARCHAR(30),
    user_agent         VARCHAR(200),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_token_expiry CHECK (expires_at <= family_expires_at),
    CONSTRAINT ck_refresh_token_replaced CHECK ((replaced_by_id IS NULL) = (replaced_at IS NULL)),
    CONSTRAINT ck_refresh_token_revoked CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL)),
    CONSTRAINT ck_refresh_token_reason CHECK (revoke_reason IS NULL
        OR revoke_reason IN ('LOGOUT', 'REUSE_DETECTED', 'PASSWORD_CHANGED', 'ADMIN_ACTION')),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_refresh_token_replaced_by FOREIGN KEY (replaced_by_id) REFERENCES refresh_token (id)
);

-- Thu hồi theo người dùng (khóa tài khoản, đổi mật khẩu) và theo family (đăng xuất, phát hiện dùng lại)
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
