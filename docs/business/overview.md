# Tổng quan dự án

## Bài toán nghiệp vụ

Quản lý chuỗi phân phối cám từ nhà cung cấp đến người tiêu dùng cuối.

### Chuỗi phân phối

```
Nhà cung cấp (NCC)
       │
       ▼
  Kho tập kết ──────► Kho bán lẻ
       │                   │
       ▼                   ▼
  Đại lý cấp 1      Khách lẻ
       │
       ▼
  Trang trại / Người dân
```

Hàng hóa nhập từ NCC vào kho tập kết, sau đó được:
- **Bán trực tiếp** cho đại lý cấp 1 hoặc trang trại
- **Điều chuyển** đến kho bán lẻ để phân phối

## Đối tượng sử dụng

| Vai trò | Mô tả |
|---|---|
| Quản lý kho | Nhập hàng, xuất hàng, điều chuyển, kiểm kê |
| Nhân viên bán hàng | Tạo đơn bán, xuất hàng cho khách |
| Quản lý hệ thống | Quản lý kho, sản phẩm, người dùng, báo cáo |

> **Ghi chú MVP:** Giai đoạn đầu có thể chưa phân quyền chi tiết. Một người dùng thực hiện tất cả chức năng.

## Phạm vi MVP

### Bắt buộc

1. Quản lý sản phẩm (CRUD, đơn vị tính)
2. Quản lý nhiều kho
3. Nhập kho từ nhà cung cấp
4. Bán hàng / xuất kho
5. Điều chuyển hàng giữa các kho
6. Lịch sử biến động tồn kho (StockTransaction)
7. Báo cáo tồn kho
8. REST API có validation và xử lý lỗi
9. Giao diện React đủ dùng
10. Kiểm thử tự động cho quy tắc nghiệp vụ
11. Docker Compose chạy local
12. Triển khai VPS + Nginx + HTTPS
13. Backup và hướng dẫn khôi phục database

### Bổ sung theo yêu cầu (2026-10-20)

- Nhân viên, chấm công (ngày công + tấn bốc vác), bảng lương tháng — quy tắc BR-EMP / BR-ATT / BR-PAY trong `rules.md`. Chưa có đăng nhập: quản lý nhập công hộ.
- Bán hàng kèm **bảng giá riêng theo từng khách** và **công nợ khách hàng** (phiếu bán + lịch sử thanh toán, chưa có phiếu thu gộp) — BR-SAL-06..14.
14. CI/CD cơ bản bằng GitHub Actions

### Xem xét nếu còn thời gian

- Giá bán theo kho / theo loại khách _(giá riêng theo từng khách đã làm — BR-SAL-06)_
- Kiểm kê và điều chỉnh tồn kho
- Xác thực và phân quyền chi tiết
- Báo cáo nâng cao

### Ngoài phạm vi

- Tích hợp cân điện tử / IoT
- Microservices, Kafka, Redis, Kubernetes
- Ứng dụng mobile

## Tiêu chí hoàn thành

- [ ] Chạy local được theo hướng dẫn
- [ ] Có dữ liệu seed phục vụ demo
- [ ] Luồng nhập, xuất, bán, điều chuyển hoạt động đúng
- [ ] Test cho các quy tắc tồn kho quan trọng
- [ ] Không xuất vượt tồn trong tình huống đồng thời
- [ ] Lịch sử tồn kho đối chiếu được
- [ ] Báo cáo tồn kho và giao diện sử dụng được
- [ ] Docker Compose hoạt động
- [ ] Hướng dẫn deploy VPS
- [ ] HTTPS, secrets, backup, phục hồi
- [ ] CI kiểm tra chất lượng
- [ ] README mô tả đúng thực tế
- [ ] Có thể tự giải thích kiến trúc, schema, transaction, index và deploy trong phỏng vấn
