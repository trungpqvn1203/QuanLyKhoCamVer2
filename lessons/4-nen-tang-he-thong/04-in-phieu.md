# In phiếu (K80)

[← Mục lục kho bài học](../README.md)

## Module: In phiếu bán và phiếu xuất kho (2026-10-28, migration V24)

- **Một phiếu, hai người đọc, hai mẫu in.** Khách cần giá, tổng tiền và số tiền bằng chữ. Lái xe và bốc vác cần kho, số bao và **số tấn** để xếp hàng và biết xe có quá tải không, nhưng *không nên* thấy giá bán. Vì vậy dữ liệu dùng chung một phiếu bán (`GET /api/sales-orders/{id}`), còn mẫu in thì tách làm hai: `SalesReceipt` và `DeliverySlip`. Backend chỉ cần trả thêm `tons` cho từng dòng và `totalTons`, tính bằng BigDecimal theo đúng công thức của BR-PRD-06, để frontend không tự nhân số thực.
- **Luồng thực tế quyết định lúc nào được in.** Kho in phiếu xuất cho lái xe khi phiếu còn **nháp**, bốc hàng xong mới hoàn tất. Thiếu hàng thì sửa nháp rồi in lại. Phiếu đã hoàn tất thì bị khóa, chỉ còn cách đảo phiếu. Vì vậy cả hai mẫu đều in được ở trạng thái nháp. Riêng phiếu khách ở trạng thái nháp có dòng "PHIẾU TẠM TÍNH" để không bị coi là chứng từ chính thức.
- **Chỉ in bản đã lưu.** Nếu đang sửa nháp mà cho in, tờ giấy sẽ mang số trên form chứ không phải số trong DB. Ví dụ vừa giảm từ 50 xuống 40 bao vì thiếu hàng nhưng chưa bấm Lưu: lái xe chở 40, còn hệ thống vẫn ghi 50. `form.isFieldsTouched()` chặn trường hợp này.
- **In bằng trình duyệt, không cần thư viện.** Mẫu in là component React → `renderToStaticMarkup` (có sẵn trong `react-dom/server`) → chuỗi HTML → `iframe` ẩn (`srcdoc`) → `print()`. Dùng iframe để CSS in tách khỏi CSS của antd trên trang chính. CSS khổ K80: `@page { size: 80mm auto; margin: 0 }`, chữ đen, không nền, vì máy in nhiệt chỉ in được màu đen. Phương án thay thế là sinh PDF ở backend (cần thêm thư viện như OpenPDF): tốn công hơn, chỉ đáng làm khi cần lưu file hoặc gửi email.
- **XSS khi in.** Tên khách và ghi chú do người dùng nhập. Nếu ghép thẳng vào chuỗi HTML (`'<div>' + customerName + '</div>'`) thì tên kiểu `<img onerror=...>` sẽ thành mã chạy được. `renderToStaticMarkup` tự escape chữ, và đã có test kiểm `<b>` biến thành `&lt;b&gt;`.
- **Số tiền bằng chữ tiếng Việt có nhiều ngoại lệ.** Các trường hợp đặc biệt: "linh" (105), "mười" khác "mươi" (10 khác 20), "mốt" (21 nhưng 11 vẫn là "mười một"), "tư" (24), "lăm" (15, 25), "không trăm" ở nhóm giữa (1.005 = một nghìn **không trăm linh năm**), và "nghìn tỷ". Hàm `vndToWords` đọc theo nhóm 3 chữ số, mỗi ngoại lệ có test riêng. Loại hàm này nên viết theo hướng **test trước**: liệt kê ví dụ đúng, rồi viết code cho qua.
- **Bảng cấu hình một dòng.** `store_info` có `CHECK (id = 1)`, giống `cash_book`. Lần lưu đầu thì tạo, các lần sau ghi đè; không tạo sẵn dòng trong migration để biết được "chưa cấu hình". API trả `configured: false` thay cho 404, vì "chưa cấu hình" là trạng thái bình thường, không phải lỗi.

### Lỗi đã gặp: `tsc --noEmit -p .` không kiểm tra gì

- `tsconfig.json` của Vite chỉ có `"files": []` và `references` trỏ tới `tsconfig.app.json` / `tsconfig.node.json`. `tsc -p .` không tự đi theo `references`, nên nó **không kiểm tra file nào** mà vẫn báo thành công. Từ đầu phiên mọi lần "tsc qua" đều vô nghĩa. Lỗi thật chỉ lộ ra khi chạy `tsc -b` (chế độ build, có đi theo `references`, cũng là lệnh trong `npm run build`): `SalesOrderForm` sau đợt đổi đơn vị tính vẫn đọc `unit` trong khi interface đã đổi thành `unitName`, nên cột ĐVT của form phiếu bán hiện "—".
- Bài học: lệnh kiểm tra cũng phải được kiểm tra. Cố ý cài một lỗi kiểu dữ liệu rồi chạy lệnh; nếu lệnh vẫn "xanh" thì nó không bảo vệ được gì. Dùng `npx tsc -b` (hoặc `-p tsconfig.app.json`).

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao phiếu xuất kho cho lái xe không in giá? Dữ liệu tách ở đâu: backend hay mẫu in?
2. Nếu cho in khi form nháp chưa lưu, kịch bản nào làm lệch giữa hàng thực giao và sổ sách?
3. Đọc thành chữ: 1.000.005, 2.000.001.000, 21, 11, 105. Giải thích "không trăm", "linh", "mốt".
4. Vì sao dùng iframe chứ không `window.print()` thẳng trên trang?
5. `tsc --noEmit -p .` báo thành công mà không kiểm tra gì: vì sao? Làm sao biết một lệnh kiểm tra có thực sự hoạt động?
