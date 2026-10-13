# Grain Warehouse — Hệ thống quản lý chuỗi phân phối cám

## Giới thiệu

Hệ thống quản lý kho và chuỗi phân phối cám theo mô hình:

**Nhà cung cấp → Kho tập kết → Đại lý cấp 1 → Trang trại/Người dân**

Hỗ trợ quản lý nhiều kho, nhập/xuất/điều chuyển hàng, theo dõi tồn kho theo thời gian thực và báo cáo.

## Chức năng chính

- 📦 Quản lý danh mục sản phẩm và đơn vị tính
- 🏭 Quản lý nhiều kho (tập kết, bán lẻ)
- 📥 Nhập kho từ nhà cung cấp
- 📤 Bán hàng / xuất kho
- 🔄 Điều chuyển hàng giữa các kho
- 📊 Lịch sử biến động và báo cáo tồn kho
- 🔐 Xác thực và phân quyền (giai đoạn mở rộng)

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot, Maven |
| Frontend | React, TypeScript, Vite |
| Database | PostgreSQL, Flyway |
| Đóng gói | Docker, Docker Compose |
| Reverse proxy | Nginx |
| CI/CD | GitHub Actions |

## Chạy local

### Yêu cầu môi trường

- JDK 21 LTS (Oracle hoặc OpenJDK)
- Apache Maven 3.9+
- Node.js 20+ và npm 10+
- Docker và Docker Compose (dành cho database dev)

### Bước chạy chi tiết

```bash
# 1. Khởi chạy PostgreSQL qua Docker Compose
docker compose -f infra/docker/docker-compose.dev.yml up -d

# 2. Khởi chạy Backend (Spring Boot 3.3.5)
cd backend
mvn spring-boot:run

# 3. Kiểm tra Backend & Database đã sẵn sàng
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/health

# 4. Khởi chạy Frontend (mở terminal mới)
cd frontend
npm install
npm run dev
# Truy cập giao diện tại: http://localhost:5173
```

## Chạy kiểm thử (Automated Tests)

```bash
# Backend smoke tests & controller tests
cd backend
mvn test

# Frontend smoke tests & type checks
cd frontend
npm test
npm run build
```

## Demo

> 🔗 Link demo: _(chưa triển khai)_

## Quyết định kỹ thuật

Xem [docs/decisions/](docs/decisions/) để hiểu các quyết định thiết kế và đánh đổi.

## Kiến thức đã học

Xem [NOTES.md](NOTES.md) — nhật ký ghi lại những gì đã học, lỗi đã gặp và cách xử lý trong quá trình xây dựng dự án.

## Tài liệu

Xem [Bản đồ tài liệu](docs/README.md) để biết hướng dẫn tra cứu chi tiết và quy tắc tối ưu context.

- [Tổng quan nghiệp vụ](docs/business/overview.md) | [Quy tắc nghiệp vụ](docs/business/rules.md) | [Thuật ngữ](docs/business/glossary.md)
- [Kiến trúc hệ thống](docs/architecture/system-design.md) | [Thiết kế Database](docs/architecture/database.md)
- [Chuẩn giao tiếp API FE ↔ BE](docs/api/contract-spec.md)
- [Lộ trình phát triển](docs/roadmap/plan.md)
- [Triển khai & Vận hành](docs/maintenance/deployment.md) | [Sao lưu & Phục hồi](docs/maintenance/backup-restore.md) | [Xử lý sự cố](docs/maintenance/troubleshooting.md)

