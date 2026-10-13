# Cẩm nang xử lý sự cố (Troubleshooting Runbook)

## 1. Quy trình chẩn đoán sự cố

Khi hệ thống gặp sự cố, tuân thủ 4 bước:
1. **Thu thập triệu chứng**: Người dùng báo lỗi gì? HTTP status code nào?
2. **Kiểm tra tầng hạ tầng**: Container còn sống không? RAM / Disk VPS có bị đầy?
3. **Kiểm tra Logs**: Backend log, DB log, Nginx access/error log.
4. **Cô lập và khắc phục**: Tái hiện local trước khi áp dụng hotfix lên Production.

---

## 2. Các sự cố phổ biến & Cách xử lý

### A. Backend không khởi động được (Crash on start)
- **Triệu chứng**: Container backend exit với code 1.
- **Nguyên nhân thường gặp**:
  - Không kết nối được PostgreSQL (DB chưa kịp khởi động hoặc sai credentials).
  - Flyway migration lỗi cú pháp SQL hoặc checksum không khớp.
- **Cách khắc phục**:
  - Kiểm tra log: `docker compose logs backend --tail 100`
  - Đảm bảo DB healthy trước khi backend start (`depends_on` với condition `service_healthy`).

### B. Lỗi 500 Internal Server Error
- **Triệu chứng**: API trả về status 500 với body ApiResponse chuẩn (`success: false`).
- **Nguyên nhân**: Exception không được bắt tường minh hoặc lỗi constraint database (ví dụ: vi phạm CHECK `quantity >= 0`).
- **Cách khắc phục**:
  - Tìm `traceId` trong response header/body và grep trong backend log.
  - Kiểm tra xem có vi phạm một trong 4 quy tắc nghiệp vụ cứng không (tồn kho âm, sai lệch điều chuyển).

### C. Lỗi CORS khi gọi từ Frontend (Dev/Staging)
- **Triệu chứng**: Browser console báo lỗi `Access to fetch blocked by CORS policy`.
- **Nguyên nhân**: Cấu hình AllowedOrigins chưa bao gồm port FE dev.
- **Cách khắc phục**:
  - Local: Dùng Vite Dev Server Proxy (chuyển hướng `/api` sang `http://localhost:8080`) để tránh hoàn toàn CORS trong quá trình phát triển.
