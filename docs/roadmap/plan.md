# Lộ trình phát triển (4 tuần)

> Đây là kế hoạch mục tiêu, không phải quy trình cứng. Thứ tự có thể thay đổi theo nhu cầu học tập. Không hy sinh tính đúng đắn để chạy theo lịch.

---

## Tuần 1 — Nền tảng và chạy local

### Mục tiêu
Có hệ thống chạy được local với một luồng nghiệp vụ nhỏ hoàn chỉnh (sản phẩm + kho).

### Checklist

- [ ] Khảo sát nghiệp vụ, trả lời câu hỏi mở trong business/rules.md _(đã xong phần Sản phẩm/Danh mục/Kho)_
- [x] Chốt schema cho bảng `category`, `product`
- [x] Chốt schema cho bảng `warehouse`
- [x] Khởi tạo Spring Boot project (Maven, Java 21)
- [x] Cấu hình PostgreSQL + Flyway _(test chạy trên DB thật `grain_warehouse_test`)_
- [x] Migration V1: bảng `app_info` (kiểm chứng Flyway hoạt động)
- [x] Migration V2: bảng `category`, `product`
- [x] Migration V3: bảng `warehouse`
- [x] Khởi tạo React + Vite project
- [ ] Docker Compose: PostgreSQL + Backend + Frontend _(hiện chỉ có PostgreSQL dev; Backend/Frontend đóng gói ở Tuần 4)_
- [x] Global exception handler (cần cho API đầu tiên)
- [x] API CRUD danh mục + sản phẩm + test
- [x] API CRUD kho + test
- [x] Seed dữ liệu demo _(`db/seed/demo-data.sql`, chạy tay bằng psql)_
- [x] Giao diện cơ bản: danh sách sản phẩm, danh sách kho + form thêm/sửa _(Ant Design, React Router — xem ADR-0002)_
- [x] Commit _(tách theo module: product, warehouse, seed, UI, bộ lọc sản phẩm)_

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

- [x] Chốt schema cho inventory, stock_document, stock_document_item, stock_transaction _(duyệt 2026-10-19)_
- [x] Migration tiếp theo: tạo các bảng nghiệp vụ kho _(V6; `stock_document` mới mở loại `INBOUND`)_
- [x] Supplier + Customer CRUD _(Supplier + liên kết NCC–SP xong 2026-10-16, V4; Customer BE + FE xong 2026-10-20, V8)_
- [x] Module nhập kho: API + service + test _(backend 2026-10-19; giao diện ở Tuần 3)_
- [ ] Module bán hàng/xuất kho: API + service + test
- [ ] Module điều chuyển kho: API + service + test
- [ ] StockTransaction ghi nhận đúng cho tất cả nghiệp vụ _(nhập + đảo phiếu nhập xong)_
- [ ] Test: không xuất vượt tồn (unit test) _(đã có cho đảo phiếu nhập; còn xuất kho)_
- [x] Test: race condition với concurrent requests _(`InboundReceiptConcurrencyTest`: hoàn tất/đảo đồng thời, cộng tồn đồng thời)_
- [ ] Test: điều chuyển rollback khi thất bại _(đã có rollback khi đảo phiếu 2 dòng; còn điều chuyển)_
- [x] Test: CHECK constraint ngăn tồn âm ở DB level _(`InventoryPersistenceTest`)_
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

- [x] Giao diện nhập kho _(2026-10-19: form phiếu trong `Drawer`; dòng hàng động `Form.List`; dòng tổng `Table.Summary`; ô SP lấy từ SP đang gán cho NCC — xem ADR-0002)_
- [ ] Giao diện bán hàng/xuất kho _(dùng lại mẫu phiếu của nhập kho)_
- [ ] Giao diện điều chuyển kho _(các bước `Steps`: chọn kho nguồn → chọn hàng → xác nhận)_
- [ ] Giao diện lịch sử biến động tồn kho _(lọc khoảng ngày `DatePicker.RangePicker`)_
- [ ] Báo cáo tồn kho (theo kho, theo sản phẩm) _(rê chuột xem tồn từng kho bằng `Popover`; trang tổng quan `Card` + `Statistic`)_ — **theo sản phẩm xong 2026-10-19** (trang Sản phẩm: tồn tổng/theo kho, đang về, Sắp hết/Hết hàng, migration V7); còn trang tổng quan và xem theo kho
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
