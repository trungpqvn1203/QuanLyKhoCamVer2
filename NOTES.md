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

<!-- Template cho các ngày tiếp theo:

## Ngày: YYYY-MM-DD

### Module: [Tên module]

**Tôi hiểu gì:**
-

**Tôi còn chưa hiểu gì:**
-

**Lỗi đã gặp và cách xử lý:**
-

**Quyết định kỹ thuật và lý do:**
-

**Hai câu hỏi tự kiểm tra:**
1.
2.

**Việc cần ôn lại:**
-

-->
