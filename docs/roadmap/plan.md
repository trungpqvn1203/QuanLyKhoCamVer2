# Lộ trình phát triển (4 tuần)

> Đây là kế hoạch mục tiêu, không phải quy trình cứng. Thứ tự có thể thay đổi theo nhu cầu học tập. Không hy sinh tính đúng đắn để chạy theo lịch.

---

## Tuần 1 — Nền tảng và chạy local

### Mục tiêu
Có hệ thống chạy được local với một luồng nghiệp vụ nhỏ hoàn chỉnh (sản phẩm + kho).

### Checklist

- [ ] Khảo sát nghiệp vụ, trả lời câu hỏi mở trong business/rules.md _(đã xong phần Sản phẩm/Danh mục)_
- [x] Chốt schema cho bảng `category`, `product`
- [ ] Chốt schema cho bảng `warehouse`
- [x] Khởi tạo Spring Boot project (Maven, Java 21)
- [x] Cấu hình PostgreSQL + Flyway _(test chạy trên DB thật `grain_warehouse_test`)_
- [x] Migration V1: bảng `app_info` (kiểm chứng Flyway hoạt động)
- [x] Migration V2: bảng `category`, `product`
- [ ] Migration V3: bảng `warehouse`
- [x] Khởi tạo React + Vite project
- [ ] Docker Compose: PostgreSQL + Backend + Frontend _(hiện chỉ có PostgreSQL dev; Backend/Frontend đóng gói ở Tuần 4)_
- [x] Global exception handler (cần cho API đầu tiên)
- [x] API CRUD danh mục + sản phẩm + test
- [ ] API CRUD kho + test
- [ ] Seed dữ liệu demo
- [ ] Giao diện cơ bản: danh sách sản phẩm, danh sách kho _(Ant Design — xem ADR-0002)_
- [ ] Commit: `feat: product and warehouse module with basic UI`

### Kiến thức cần nắm
- Spring Boot project structure
- Flyway migration workflow
- JPA Entity, Repository, Service, Controller
- REST API conventions
- Docker Compose basics

---

## Tuần 2 — Nghiệp vụ lõi

### Mục tiêu
Hoàn thành nhập kho, bán hàng, điều chuyển với đầy đủ StockTransaction và test.

### Checklist

- [ ] Chốt schema cho inventory, stock_document, stock_document_item, stock_transaction
- [ ] Migration tiếp theo: tạo các bảng nghiệp vụ kho
- [ ] Supplier + Customer CRUD (nếu cần)
- [ ] Module nhập kho: API + service + test
- [ ] Module bán hàng/xuất kho: API + service + test
- [ ] Module điều chuyển kho: API + service + test
- [ ] StockTransaction ghi nhận đúng cho tất cả nghiệp vụ
- [ ] Test: không xuất vượt tồn (unit test)
- [ ] Test: race condition với concurrent requests
- [ ] Test: điều chuyển rollback khi thất bại
- [ ] Test: CHECK constraint ngăn tồn âm ở DB level
- [ ] Commit sau mỗi module hoàn chỉnh

### Kiến thức cần nắm
- Database transaction (ACID)
- @Transactional trong Spring
- Row locking, SELECT FOR UPDATE
- Conditional update
- Race condition và cách kiểm thử
- Deadlock prevention (lock ordering)

---

## Tuần 3 — Giao diện và chất lượng

### Mục tiêu
Giao diện sử dụng được cho tất cả nghiệp vụ chính. Hệ thống ổn định và có đủ test.

### Checklist

- [ ] Giao diện nhập kho
- [ ] Giao diện bán hàng/xuất kho
- [ ] Giao diện điều chuyển kho
- [ ] Giao diện lịch sử biến động tồn kho
- [ ] Báo cáo tồn kho (theo kho, theo sản phẩm)
- [ ] Phân trang cho danh sách dài
- [ ] Xác thực và phân quyền cơ bản (nếu trong phạm vi)
- [ ] Global error handling (backend + frontend)
- [ ] EXPLAIN ANALYZE cho truy vấn chính
- [ ] Tối ưu index nếu cần
- [ ] Kiểm tra N+1 query
- [ ] Rà soát bảo mật cơ bản
- [ ] Integration test cho luồng chính
- [ ] Commit khi ổn định

### Kiến thức cần nắm
- React state management
- API error handling patterns
- Pagination (offset vs cursor)
- EXPLAIN ANALYZE đọc kết quả
- Index strategy
- Nguyên tắc bảo mật API cơ bản

---

## Tuần 4 — Triển khai và hoàn thiện

### Mục tiêu
Hệ thống chạy trên VPS với HTTPS, CI/CD, backup. README đầy đủ để demo.

### Checklist

- [ ] Dockerfile cho backend (multi-stage build)
- [ ] Dockerfile cho frontend (build → Nginx serve)
- [ ] Docker Compose production
- [ ] Chuẩn bị VPS: SSH, firewall, Docker
- [ ] Nginx config: reverse proxy + HTTPS
- [ ] Let's Encrypt certificate
- [ ] Quản lý secrets trên VPS
- [ ] GitHub Actions: lint + test + build
- [ ] GitHub Actions: deploy (manual trigger hoặc on push)
- [ ] Migration chạy đúng trên production
- [ ] Health check endpoint
- [ ] Backup script PostgreSQL
- [ ] Thử khôi phục backup ít nhất 1 lần
- [ ] Kiểm tra không mở port PostgreSQL ra Internet
- [ ] Cập nhật README với thông tin thực tế
- [ ] Cập nhật NOTES.md tổng kết
- [ ] Chuẩn bị demo script
- [ ] Commit: `chore: production deployment with CI/CD`

### Kiến thức cần nắm
- Docker multi-stage build
- Docker Compose networking
- Nginx reverse proxy configuration
- HTTPS / TLS / Let's Encrypt
- GitHub Actions workflow syntax
- VPS security basics (SSH key, firewall, no public DB)
- pg_dump / pg_restore
- Rollback strategy

---

## Lưu ý quan trọng

1. **Thứ tự linh hoạt:** Có thể học Docker/CI/CD sớm hơn nếu muốn, không bắt buộc theo tuần.
2. **Mỗi module có commit riêng:** Không gộp nhiều module vào một commit lớn.
3. **Không hy sinh test:** Đừng bỏ test để chạy kịp deadline.
4. **Hỏi khi không hiểu:** Đừng copy-paste mà không hiểu.
5. **Ghi NOTES.md:** Mỗi buổi học nên có ít nhất một entry.
