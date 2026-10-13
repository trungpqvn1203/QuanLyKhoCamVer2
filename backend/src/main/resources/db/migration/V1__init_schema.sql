-- Migration V1: Bảng kiểm chứng khởi tạo ứng dụng và hoạt động của Flyway
CREATE TABLE IF NOT EXISTS app_info (
    id SERIAL PRIMARY KEY,
    app_name VARCHAR(100) NOT NULL,
    version VARCHAR(20) NOT NULL,
    initialized_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_info (app_name, version)
VALUES ('Grain Warehouse System', '0.0.1');
