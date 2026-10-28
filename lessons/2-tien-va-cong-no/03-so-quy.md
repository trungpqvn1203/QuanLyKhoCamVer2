# Sổ quỹ — tiền mặt và tài khoản

[← Mục lục kho bài học](../README.md)

## Module: Sổ quỹ — tiền mặt và tài khoản (2026-10-26, migration V19)

- **VIEW thay vì chép dữ liệu.** Tiền đã nằm sẵn trong 4 bảng thanh toán (`sale_payment`, `supplier_payment`, `payroll_payment`, `employee_advance`). Sổ quỹ là `CREATE VIEW cash_flow_entry AS ... UNION ALL ...`: một bảng ảo, mỗi lần đọc PostgreSQL chạy lại câu SELECT. Ưu điểm: không bao giờ lệch (không có bản sao), không sửa module đã duyệt. Nhược điểm: VIEW không khóa được, không lưu được số dư — nên khi cần **chặn quỹ âm** hay **nhiều tài khoản** phải chuyển sang bảng sổ quỹ riêng có số dư lưu và khóa `FOR UPDATE`, giống tồn kho.
- **Dấu tiền đặt ở một chỗ.** Mỗi nguồn quy về một cột `amount` có dấu: thu khách `+`, trả NCC / trả lương / tạm ứng `−`, dòng hoàn đảo dấu. Phiếu thu/chi giữ nguyên `direction` của phiếu gốc ngay cả ở phiếu hoàn; dấu = `(direction = IN) = (type = VOUCHER) ? + : −`. Nhờ vậy mọi tổng, số dư, báo cáo chỉ là `SUM(amount)`.
- **Bỏ "trừ tạm ứng" khỏi sổ quỹ** (`WHERE method <> 'ADVANCE_DEDUCTION'`): tiền đã ra khỏi quỹ lúc ứng; nếu tính thêm lúc trừ lương thì một khoản tiền bị đếm hai lần. Đây là hệ quả đã ghi chú từ module công nợ nhân viên.
- **Số dư lũy kế bằng hàm cửa sổ:** `SUM(amount) OVER (ORDER BY entry_date, created_at, source, source_id)` cộng dồn từng dòng theo thứ tự. Thứ tự phải **đủ chặt** (thêm `source, source_id`): nếu hai dòng trùng ngày và trùng `created_at` (cùng một transaction — `now()` trả thời điểm bắt đầu transaction nên mọi dòng chèn trong đó có cùng giá trị), số dư lũy kế sẽ đổi giữa các lần chạy. Số dư đầu khoảng = đầu kỳ + tổng các dòng từ ngày đầu kỳ tới **ngày hôm trước** — thử đột biến lấy tới cả ngày đầu khoảng thì test bắt ngay (sai 1.000.000 do tính trùng một dòng).
- **Đầu kỳ là cấu hình, nhưng có dấu vết.** Đầu kỳ sửa được (khác chứng từ), vì người dùng có thể nhập sai số tiền thực tế. Nhưng sửa đầu kỳ làm đổi **mọi** số dư, nên mỗi lần sửa ghi một dòng `cash_book_change` (cũ → mới, ai, lúc nào) — bảng này bất biến.
- **Một hàm trigger cho nhiều bảng:** `prevent_cash_change()` dùng `TG_TABLE_NAME` trong thông báo, gắn cho cả `cash_voucher` và `cash_book_change`. Các module trước mỗi bảng một hàm; cách này gọn hơn khi số bảng bất biến tăng.
- **Lỗi đã gặp:**
  - Thiếu query param bắt buộc (`GET /api/cash-book` không có `account`) trả **500**, vì `GlobalExceptionHandler` chưa xử lý `MissingServletRequestParameterException` — lỗi tiềm ẩn từ trước, chỉ lộ ra khi lần đầu có endpoint có `@RequestParam` bắt buộc. Đã thêm handler trả 400 `details: [{ field: "account" }]`.
  - Test chèn `'WALLET'` để thử CHECK tài khoản nhưng cột là `VARCHAR(4)` → bị chặn bởi độ dài trước khi tới CHECK. Muốn test một ràng buộc thì dữ liệu phải vượt qua mọi ràng buộc khác.
  - Test frontend dùng lại một `Response` giả cho hai lần `fetch` → lần hai lỗi vì body chỉ đọc được một lần. Mock phải tạo `Response` mới mỗi lần gọi.

### Câu hỏi ôn tập (để cuối dự án)

1. VIEW khác bảng ở điểm nào? Vì sao VIEW đủ cho sổ quỹ hiện tại nhưng không đủ nếu phải chặn quỹ âm?
2. Vì sao dòng "trừ tạm ứng" không xuất hiện trong sổ quỹ? Nếu có thì số dư sai thế nào?
3. Số dư lũy kế dùng `ORDER BY entry_date, created_at, source, source_id` — bỏ hai cột cuối thì có thể sai ở trường hợp nào?
4. Đầu kỳ sửa được còn phiếu thu/chi thì không — ranh giới giữa "cấu hình" và "chứng từ" là gì?
