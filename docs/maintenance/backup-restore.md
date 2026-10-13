# Sao lưu & Phục hồi Database (Backup & Restore)

## 1. Nguyên tắc cốt lõi

- **Sao lưu trước khi thay đổi**: Luôn sao lưu database trước khi deploy bản cập nhật có Flyway migration mới.
- **Quy tắc 3-2-1**: Tối thiểu 1 bản backup lưu ngoài VPS (tải về local hoặc lưu trên Cloud Object Storage).
- **Kiểm chứng phục hồi**: Bản backup chỉ có giá trị khi đã diễn tập phục hồi thành công ít nhất 1 lần.

---

## 2. Lệnh sao lưu (Backup)

### Sao lưu toàn bộ Database (Docker)

```bash
# Thực hiện trên VPS (hoặc local container)
docker exec <db-container-name> pg_dump -U $POSTGRES_USER $POSTGRES_DB | gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz
```

### Sao lưu tự động qua Cron (VPS)

Thêm cron job hàng ngày vào 02:00 sáng:

```bash
0 2 * * * docker exec grain-warehouse-db pg_dump -U postgres grain_warehouse | gzip > /opt/backups/grain_warehouse_$(date +\%Y\%m\%d).sql.gz && find /opt/backups -type f -mtime +14 -name "*.sql.gz" -delete
```
*(Tự động dọn dẹp các bản backup cũ hơn 14 ngày)*

---

## 3. Lệnh phục hồi (Restore)

```bash
# 1. Dừng backend để tránh ghi dữ liệu dở dang
docker compose stop backend

# 2. Xả nén và import dữ liệu vào database
gunzip -c backup_20261013_120000.sql.gz | docker exec -i <db-container-name> psql -U $POSTGRES_USER $POSTGRES_DB

# 3. Khởi động lại backend và kiểm tra health check
docker compose start backend
curl http://localhost:8080/actuator/health
```

---

## 4. Xử lý lỗi Flyway Migration

Flyway Community **không tự động rollback** migration thất bại:
- Nếu migration thất bại giữa chừng: bảng `flyway_schema_history` sẽ ghi nhận trạng thái `failed = true`.
- **Cách xử lý**:
  1. Phục hồi DB từ bản backup trước migration.
  2. Hoặc sửa lỗi script SQL, chạy `flyway repair` để sửa metadata history rồi chạy lại.
