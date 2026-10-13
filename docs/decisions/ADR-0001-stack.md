# ADR-0001: Lựa chọn stack công nghệ

## Trạng thái

✅ Đã chấp nhận — 2026-10-13

## Bối cảnh

Xây dựng hệ thống quản lý kho cám từ đầu. Mục tiêu kép:
1. Sản phẩm chạy thực tế, đủ chất lượng đưa vào CV
2. Học và hiểu bản chất công nghệ để phỏng vấn Java Backend

Người phát triển là Fresher có kinh nghiệm nghiệp vụ kho nhưng kiến thức kỹ thuật nền tảng còn cần củng cố. Thời gian: ~1 tháng.

## Quyết định

| Thành phần | Chọn | Phiên bản |
|---|---|---|
| Backend language | Java | 21 LTS |
| Backend framework | Spring Boot | (chọn phiên bản tương thích khi khởi tạo) |
| Build tool | Maven | 3.9.x |
| Database | PostgreSQL | (chọn phiên bản ổn định khi khởi tạo) |
| DB migration | Flyway | (tương thích Spring Boot) |
| Frontend | React + TypeScript | với Vite |
| Testing | JUnit 5 + Spring Boot Test | Testcontainers khi phù hợp |
| Container | Docker + Docker Compose | |
| Reverse proxy | Nginx | |
| CI/CD | GitHub Actions | |
| Architecture | Modular Monolith | |

## Phương án thay thế đã xem xét

### Backend: Spring Boot vs. các framework khác

| Framework | Ưu điểm | Nhược điểm | Lý do không chọn |
|---|---|---|---|
| **Spring Boot** ✅ | Ecosystem lớn, nhiều việc làm, tài liệu phong phú | Learning curve ban đầu | — |
| Quarkus | Khởi động nhanh, native image | Ít việc làm ở VN, ít tài liệu tiếng Việt | Không phù hợp mục tiêu xin việc |
| Micronaut | Nhẹ, compile-time DI | Ecosystem nhỏ hơn | Ít phổ biến trong tuyển dụng |
| Express.js (Node) | Đơn giản, nhanh phát triển | Không phải Java, typing yếu | Mục tiêu là Java Backend |

### Database: PostgreSQL vs. MySQL

| DB | Ưu điểm | Nhược điểm |
|---|---|---|
| **PostgreSQL** ✅ | CHECK constraint mạnh, NUMERIC chính xác, JSON support, window functions | Ít phổ biến hơn MySQL ở một số công ty VN |
| MySQL | Phổ biến, nhiều hosting hỗ trợ | CHECK constraint yếu hơn (trước 8.0.16), DECIMAL ít linh hoạt |

**Lý do chọn PostgreSQL:** CHECK constraint là yêu cầu cứng cho ngăn tồn âm. PostgreSQL hỗ trợ tốt hơn các ràng buộc toàn vẹn dữ liệu.

### Build tool: Maven vs. Gradle

| Tool | Ưu điểm | Nhược điểm |
|---|---|---|
| **Maven** ✅ | Cấu trúc chuẩn, dễ đọc XML, nhiều ví dụ | Verbose, chậm hơn Gradle |
| Gradle | Build nhanh, DSL gọn | Learning curve với Groovy/Kotlin DSL |

**Lý do chọn Maven:** Dễ hiểu và debug hơn cho Fresher. Verbose nhưng rõ ràng.

### Architecture: Modular Monolith vs. Microservices

| Kiến trúc | Ưu điểm | Nhược điểm |
|---|---|---|
| **Modular Monolith** ✅ | Đơn giản, transaction dễ, deploy dễ | Khó scale từng module |
| Microservices | Scale linh hoạt, team lớn | Phức tạp: saga, service mesh, monitoring |

**Lý do chọn Modular Monolith:** Một người phát triển, MVP, cần transaction xuyên module đơn giản. Microservices thêm phức tạp không cần thiết.

## Đánh đổi

1. **Java 21 vs. Java 17:** Java 21 mới hơn, có virtual threads. Đánh đổi: ít ví dụ cũ nhưng là LTS mới nhất, Spring Boot hỗ trợ tốt.
2. **Monolith:** Nếu cần scale sau này, phải tái cấu trúc. Chấp nhận vì MVP không cần scale.
3. **Maven verbose:** Chấp nhận vì dễ đọc, phù hợp cho việc học.
4. **PostgreSQL:** Một số shared hosting không hỗ trợ, nhưng VPS tự quản lý nên không ảnh hưởng.

## Hệ quả

- Mọi thành viên (hiện tại: 1 người) cần hiểu Java, Spring Boot, SQL, React
- Deploy bằng Docker Compose trên VPS, không cần Kubernetes
- CI/CD đơn giản với GitHub Actions
- Không thêm Kafka, Redis, hay công nghệ khác khi chưa có nhu cầu rõ ràng
