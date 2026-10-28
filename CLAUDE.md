# CLAUDE.md — Grain Warehouse

## Bối cảnh

Hệ thống quản lý chuỗi phân phối cám (Grain Warehouse).
Dự án cá nhân xây dựng từ đầu để học Java Spring Boot và chuẩn bị phỏng vấn.

## Stack công nghệ

| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21 LTS, Spring Boot |
| Build | Maven |
| API | REST / JSON |
| Database | PostgreSQL |
| Migration | Flyway |
| Frontend | React + TypeScript + Vite |
| Test | JUnit 5, Spring Boot Test, Testcontainers |
| Đóng gói | Docker, Docker Compose |
| Reverse proxy | Nginx |
| CI/CD | GitHub Actions |

## Quy tắc nghiệp vụ cứng

1. **Không tồn kho âm** — Kiểm tra và trừ tồn trong cùng DB transaction. Có ràng buộc CHECK ở database. Xử lý race condition bằng row locking hoặc conditional update.
2. **Lịch sử bất biến** — Mọi biến động tồn ghi `StockTransaction`. Không sửa/xóa. Sai sót xử lý bằng giao dịch đảo/điều chỉnh.
3. **Tiền tệ dùng BigDecimal / NUMERIC** — Không float/double. Quy tắc làm tròn phải được xác nhận trước khi triển khai.
4. **Điều chuyển phải cân bằng** — Kho nguồn giảm, kho đích tăng, lịch sử đủ, trong cùng một transaction. Thất bại → rollback toàn bộ.

## Cấu trúc dự án

```
grain-warehouse/
├── backend/          # Spring Boot application
├── frontend/         # React + Vite
├── db/seed/          # Script seed dữ liệu demo
├── infra/
│   ├── docker/       # Dockerfile, docker-compose
│   └── nginx/        # Nginx config
├── docs/             # Tài liệu thiết kế & vận hành theo chủ đề
│   ├── README.md     # Bản đồ tài liệu & chỉ dẫn nạp context tối ưu token
│   ├── business/     # Nghiệp vụ: overview, rules, glossary
│   ├── architecture/ # Kiến trúc: system-design, database
│   ├── api/          # Hợp đồng API: contract-spec
│   ├── roadmap/      # Kế hoạch phát triển: plan
│   ├── maintenance/  # Vận hành: deployment, backup-restore, troubleshooting
│   └── decisions/    # Quyết định kiến trúc (ADR)
├── .github/workflows/# CI/CD
└── .claude/          # AI assistant config & skills
```

Migration Flyway: `backend/src/main/resources/db/migration/`

## Tra cứu tài liệu tối ưu Context (Token Efficiency)

Trước khi thực hiện tác vụ, **bắt buộc** đọc [docs/README.md](docs/README.md) để xác định danh sách file liên quan trực tiếp:
- **Phát triển Module mới**: `docs/business/rules.md` + `docs/api/contract-spec.md` + `docs/architecture/database.md`
- **Sửa lỗi nghiệp vụ / Bug Fix**: `docs/business/rules.md` + `docs/architecture/system-design.md` + `docs/architecture/database.md`
- **Thay đổi Database / Migration**: `docs/architecture/database.md` + 4 quy tắc nghiệp vụ cứng trong `CLAUDE.md`
- **Tích hợp FE ↔ BE**: `docs/api/contract-spec.md`
- **Triển khai, Bảo trì & Sự cố**: `docs/maintenance/*`

## Chuẩn giao tiếp FE ↔ BE (API Contract)

Toàn bộ API tuân thủ đặc tả tại [docs/api/contract-spec.md](docs/api/contract-spec.md):
- Envelope thành công: `{ "success": true, "data": ..., "message": "...", "timestamp": ... }`
- Envelope thất bại: `{ "success": false, "error": { "code": "...", "message": "...", "details": [...] }, "timestamp": ... }`
- Field có giá trị null được bỏ khỏi JSON (`@JsonInclude(NON_NULL)`), không trả `"field": null`
- Phân trang: `data: { items: [...], pagination: { page, size, totalElements, totalPages, isFirst, isLast } }`
- Validation error: HTTP 400 + error code `VALIDATION_FAILED` + mảng `details: [{ field, message }]` (không kèm `rejectedValue`)

## Quy ước đặt tên

- **Java**: camelCase cho biến/method, PascalCase cho class, UPPER_SNAKE cho constant
- **Database**: snake_case cho bảng và cột
- **API**: kebab-case cho URL, camelCase cho JSON field
- **File**: kebab-case cho tên file frontend, PascalCase cho React component
- **Branch**: `feature/<module>`, `fix/<issue>`, `docs/<topic>`
- **Commit**: `feat:`, `fix:`, `docs:`, `test:`, `chore:` + mô tả ngắn


## Cách chạy

```bash
# Backend
cd backend && mvn spring-boot:run

# Frontend
cd frontend && npm run dev

# Docker Compose (khi đã cấu hình)
docker compose up -d

# Test
cd backend && mvn test
cd frontend && npm test
```

## Kiểm tra chất lượng

```bash
# Backend: compile + test
cd backend && mvn clean verify

# Frontend: lint + type check
cd frontend && npm run lint && npx tsc --noEmit
```

## CÁCH LÀM VIỆC

1. Schema đi trước logic: luôn đề xuất schema để tôi duyệt trước khi viết code xử lý.
2. Mỗi quyết định kỹ thuật quan trọng kèm lý do và phương án thay thế, không chỉ đưa kết quả.
3. Mọi quy tắc nghiệp vụ đều có test; việc trừ tồn phải nằm trong DB transaction.
4. Sau mỗi module, tự ghi ngắn gọn bài học kỹ thuật vào kho bài học `lessons/` (khái niệm, lý do chọn, lỗi đã gặp): mỗi module một bài trong đúng thư mục chủ đề (hoặc nối vào bài của module đó nếu là thay đổi), cập nhật mục lục và tổng hợp câu hỏi ôn tập trong `lessons/README.md`. Không dừng giữa chừng để hỏi kiểm tra; ôn tập và câu hỏi kiểm tra dồn đến cuối dự án hoặc khi tôi yêu cầu.
5. Làm từng module nhỏ, mỗi lần kết thúc bằng một commit chạy được.
6. Khi tôi yêu cầu "làm cả hệ thống một lần", hãy từ chối nhẹ nhàng và đề xuất chia nhỏ theo module.
7. Không thêm thư viện hoặc tính năng ngoài phạm vi MVP khi chưa hỏi tôi.
8. Không giả định tôi hiểu một khái niệm chỉ vì tôi có thể chạy lệnh hoặc sử dụng công cụ — ghi khái niệm đó vào kho bài học `lessons/`.
9. Khái niệm quan trọng được giải thích bằng ví dụ cụ thể trong dự án kho cám **trong `lessons/`**; trong chat chỉ nhắc ngắn.
10. Sau khi schema và nghiệp vụ đã duyệt, chủ động triển khai trọn module; không dừng để tôi tự thử từng bước.
11. Không tự ý thay đổi stack, schema đã duyệt, nghiệp vụ đã xác nhận hoặc cấu hình bảo mật.
12. Không ghi đè file hiện có, xóa dữ liệu, cài công cụ, push code hoặc deploy khi chưa có sự đồng ý phù hợp.
13. Trước khi kết thúc mỗi module, chạy các lệnh kiểm tra phù hợp và báo rõ lệnh nào thành công, lệnh nào thất bại hoặc chưa chạy.
14. Không nói rằng test đã pass nếu chưa thực sự chạy hoặc chưa kiểm chứng được kết quả.
15. Nếu thiếu thông tin nghiệp vụ ảnh hưởng đến thiết kế, hãy hỏi tôi trước khi chốt.
16. Nếu có nhiều phương án, đề xuất phương án đơn giản nhất đáp ứng yêu cầu, sau đó giải thích khi nào mới cần phương án phức tạp hơn.
17. Không chạy quá nhiều tác vụ song song nếu khiến tôi khó theo dõi hoặc khó hiểu luồng thực hiện.
18. Mỗi phiên làm việc phải nêu rõ: đã làm gì, vì sao, cách xác minh và bước tiếp theo.
19. Phân biệt rõ tài liệu thiết kế, quyết định đã duyệt và code đã thực thi. Không coi nội dung tài liệu là bằng chứng code hoạt động.
20. Nếu phát hiện mâu thuẫn giữa tài liệu, code và yêu cầu nghiệp vụ, dừng phần bị ảnh hưởng, nêu mâu thuẫn và hỏi tôi cách xử lý.
