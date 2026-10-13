# NOTES.md — Nhật ký học tập

Ghi lại quá trình học và xây dựng dự án Grain Warehouse.

---

## Ngày: 2026-10-13

### Module: Khởi tạo dự án

**Tôi hiểu gì:**
- Cấu trúc thư mục của một dự án full-stack Java + React
- Vai trò của từng file cấu hình (.gitignore, .env.example)
- Tại sao cần tách migration (Flyway) khỏi seed data

**Tôi còn chưa hiểu gì:**
- _(Ghi lại khi gặp khái niệm mới)_

**Lỗi đã gặp và cách xử lý:**
- _(Ghi lại khi gặp lỗi)_

**Quyết định kỹ thuật và lý do:**
- Chọn Java 21 LTS vì ổn định, hỗ trợ dài hạn, tương thích Spring Boot
- Dùng Flyway quản lý migration trong backend thay vì thư mục db/migrations riêng để tránh trùng trách nhiệm
- Dùng modular monolith thay vì microservices vì đơn giản, phù hợp với Fresher và quy mô MVP

**Hai câu hỏi tự kiểm tra:**
1. Tại sao không nên commit file .env vào Git? Hậu quả nếu lỡ commit?
2. Flyway quản lý migration bằng cách nào? Tại sao không dùng `ddl-auto=update` cho production?

**Việc cần ôn lại:**
- _(Cập nhật sau mỗi buổi học)_

---

## Ngày: 2026-10-14

### Module: Phase 3 — Project Foundation

**Tôi hiểu gì:**
- Cách dựng ứng dụng Spring Boot 3.3.5 với Java 21 LTS từ Maven `pom.xml`.
- Cách tích hợp Flyway migration (`V1__init_schema.sql`) để phiên bản hóa cấu trúc database ngay từ đầu.
- Cấu hình đa môi trường qua Spring Profiles (`application.yml` chung, `application-dev.yml` cho dev, `application-test.yml` cho test).
- Chuẩn hóa cấu trúc ApiResponse (`success`, `data`, `message`, `error`, `timestamp`) giúp Frontend và Backend đồng bộ chặt chẽ.
- Cấu hình proxy trong `vite.config.ts` để FE (5173) gọi trực tiếp endpoint `/api` của BE (8080) mà không gặp rào cản CORS trong dev.

**Tôi còn chưa hiểu gì:**
- Cách Flyway phát hiện file migration bị sửa đổi (checksum validation) và cách chạy `flyway:repair`.
- Cơ chế `@MockBean` hoạt động ra sao trong Spring Slice Test (`@WebMvcTest`).

**Lỗi đã gặp và cách xử lý:**
- Lỗi khóa file trên Windows khi chạy `mvn clean` (tiến trình khác đang giữ folder `target/`): chạy `mvn test` mà không xóa ép buộc nếu đang có process đọc.
- Lỗi toán tử `&&` trên PowerShell: dùng dấu `;` để ngăn cách lệnh tuần tự trên PowerShell.

**Quyết định kỹ thuật và lý do:**
- Tạo endpoint `/api/health` trả về kết nối DB thật (qua bảng `app_info`) song song với `/actuator/health` để có kiểm chứng cả ứng dụng lẫn database.
- Đặt `hibernate.ddl-auto: validate` để đảm bảo Hibernate không tự ý sinh schema mà bắt buộc đi qua Flyway migration.

**Hai câu hỏi tự kiểm tra:**
1. Tại sao trong môi trường dev lại nên dùng Vite proxy (`/api` -> `http://localhost:8080`) thay vì cấu hình CORS mở rộng rãi (`@CrossOrigin("*")`) trên toàn bộ Controller của Spring Boot?
2. Khi dùng Flyway, nếu một đồng nghiệp lỡ sửa nội dung của file `V1__init_schema.sql` sau khi DB dev đã migrate rồi, chuyện gì sẽ xảy ra khi khởi động lại backend?

**Việc cần ôn lại:**
- Chu trình khởi tạo Spring Application Context và thứ tự nạp profile.
- Cú pháp migration Flyway và bảng `flyway_schema_history`.

---
