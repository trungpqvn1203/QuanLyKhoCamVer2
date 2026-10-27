# Bán hàng, bảng giá theo khách, công nợ khách

[← Mục lục kho bài học](../README.md)

## Module: Bán hàng, bảng giá theo khách, công nợ — backend (2026-10-20, V13)

- **Snapshot + nhật ký, lần thứ hai** — `stock_document.paid_amount` đối với `sale_payment` giống như `inventory` đối với `stock_transaction`. Nhật ký (`sale_payment`) bất biến và đầy đủ, còn snapshot (`paid_amount`) cho phép đọc nhanh và đặt được ràng buộc ở DB: `CHECK (paid_amount <= total_amount)`. Ví dụ phiếu PX000007 tổng 10 triệu, thu 4 triệu tiền mặt rồi 3 triệu chuyển khoản: nhật ký có 2 dòng, `paid_amount = 7.000.000`, còn nợ 3 triệu. Nếu chỉ tính bằng `SUM` thì DB không có cột nào để đặt CHECK, và việc "không thu quá" chỉ còn trông vào code Java.
  _Đánh đổi:_ hai nơi phải luôn khớp nhau, nên **mọi** đường thu/hoàn tiền đều phải cập nhật cả hai trong cùng transaction.
- **Thu tiền cũng phải khóa phiếu** — thu tiền không làm thay đổi tồn kho, nhưng vẫn là "đọc số còn nợ → so sánh → ghi". Nếu hai người cùng thu 400.000 cho phiếu còn nợ 1 triệu mà không khóa, cả hai đều đọc thấy "còn 1 triệu" rồi cùng ghi. `SalesOrderConcurrencyTest` cho 5 luồng cùng thu: kết quả đúng 2 thành công, 3 luồng còn lại nhận thông báo "vượt số còn nợ". Điểm nguy hiểm: Hibernate ghi `paid_amount = 400000` (giá trị tuyệt đối, không phải `paid_amount + 400000`). Không khóa thì xảy ra **lost update**: nhật ký có 2 dòng × 400k, còn snapshot chỉ ghi 400k. CHECK `paid <= total` không phát hiện được, vì 400k vẫn nhỏ hơn 1 triệu. **Đã thử bỏ khóa:** cả 5 lần thu đều thành công, tức 2 triệu cho phiếu chỉ nợ 1 triệu; hoàn tất đồng thời cũng thành công 5/5, tồn bị trừ 5 lần. Test fail đúng như dự đoán.
- **Trả tiền kết hợp = nhiều dòng, không phải nhiều cột** — khách trả 300k tiền mặt + 500k chuyển khoản thì ghi 2 dòng, mỗi dòng có `method` riêng. Không thêm cột `cash_amount`, `transfer_amount`: cách đó khiến việc "hoàn khoản chuyển khoản ghi nhầm" và "tổng thu theo hình thức trong tháng" phức tạp hơn. Sau này có thêm ví điện tử chỉ cần nới CHECK, không phải thêm cột.
- **Bảng giá chỉ để gợi ý, giá thật nằm trên dòng phiếu** — `customer_product_price` không lưu lịch sử. Dòng phiếu đã chụp `unit_price` nên đổi bảng giá từ 310k lên 350k không làm phiếu cũ đổi theo (test `orderKeepsItsOwnPrice`). Cùng nguyên tắc với bản chụp đơn giá ở bảng lương.
- **Upsert một câu SQL** — `INSERT … ON CONFLICT (customer_id, product_id) DO UPDATE`. Nếu "tìm → chưa có thì INSERT" bằng hai câu, hai người cùng đặt giá lần đầu sẽ có một người dính lỗi trùng UNIQUE.
- **CHECK theo loại phiếu** — `ck_stock_document_customer` ràng buộc: `INBOUND` thì không có khách, `OUTBOUND` thì bắt buộc có khách và không có NCC. Một bảng dùng chung cho nhiều loại chứng từ vẫn giữ được ràng buộc riêng của từng loại, nhờ mẫu `type <> 'X' OR (điều kiện của X)`.

### Lỗi đã gặp

- **Overload varargs nuốt nhầm lời gọi** — trong test có `createDraft(String... lines)` và `createDraft(String body)`. Gọi `createDraft(line(...))` với **một** dòng thì Java chọn bản `String body`, vì bản không varargs được ưu tiên. Kết quả là 17 test nhận 400, vì JSON gửi lên chỉ là một dòng hàng. Cách sửa: đổi tên thành `createDraftFrom(String body)`. Bài học: không overload varargs với một bản có cùng kiểu tham số.
- **`@Modifying` không tự xóa persistence context** — sau câu upsert viết tay, đọc lại bảng giá trong cùng transaction vẫn thấy giá cũ (310.000 thay vì 305.000,50), vì Hibernate trả entity đang nằm trong bộ nhớ. Cách sửa: `@Modifying(flushAutomatically = true, clearAutomatically = true)`. Lỗi này cùng gốc với lỗi "test đọc dữ liệu cũ" ở module nhân viên.
- **PostgreSQL hủy cả transaction sau lỗi đầu tiên** — một test kiểm cả "chặn UPDATE" lẫn "chặn DELETE": câu thứ hai báo `current transaction is aborted` (SQL state 25P02) thay vì lỗi trigger. Cách sửa: mỗi test chỉ kiểm một vi phạm. (Muốn kiểm nhiều thì phải dùng SAVEPOINT.)

### Frontend (giao diện bán hàng, công nợ, bảng giá)

- **Một khung cho cả vòng đời phiếu** — `SalesOrderDrawer` dùng lại cách làm của phiếu nhập: Nháp hiện form; Hoàn tất hiện phần xem + Thu tiền / Hoàn khoản thu / Đảo phiếu. Mọi thao tác backend trả về toàn bộ phiếu, nên chỉ cần `setOrder(updated)` là màn hình khớp DB, không phải tự tính "còn nợ" ở trình duyệt.
- **Giá gợi ý ≠ giá ép buộc** — chọn SP thì giá riêng của khách được điền sẵn, và dưới ô có dòng "Giá riêng". Người bán sửa giá thì dòng đó chuyển thành cảnh báo "Giá riêng 310.000 đ", để thấy ngay mình đang bán lệch giá.
- **Cảnh báo ở trình duyệt, chặn ở backend** — cột "Tồn kho" và chữ "Vượt tồn" chỉ để báo sớm. Người khác có thể bán cùng lúc, nên chỉ `UPDATE … WHERE quantity >= :q` lúc hoàn tất mới là chốt chặn thật. Hộp thu tiền cũng kiểm "không vượt nợ" trên trình duyệt, nhưng backend kiểm lại sau khi khóa phiếu.
- **Chạy thử thật bằng Playwright** — các test đơn vị đều pass, nhưng khi chạy giao diện mới thấy phiếu **Đã đảo** vẫn hiện "Còn nợ 620.000 đ" màu đỏ. Nguyên nhân: backend tính `total − paid` mà không xét trạng thái. Đã sửa `getOutstandingAmount()` để trả 0 khi phiếu không ở trạng thái Hoàn tất, và thêm assertion vào test. Bài học: test chỉ kiểm được điều mình nghĩ ra để kiểm; nhìn vào màn hình thật mới lộ ra chỗ con số đúng công thức nhưng sai nghĩa nghiệp vụ.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao phiếu đã thu tiền thì không cho đảo? Nếu cho đảo, `paid_amount` và CHECK `paid <= total` sẽ ra sao?
2. Nếu bỏ khóa `FOR UPDATE` ở `recordPayment` thì vì sao CHECK `paid <= total` không cứu được? Viết lại câu cập nhật thế nào để DB tự chặn mà không cần khóa? (Gợi ý: `UPDATE … SET paid_amount = paid_amount + :x WHERE … AND paid_amount + :x <= total_amount`.)
3. Vì sao bảng giá không cần lưu lịch sử giá mà vẫn tra được "tháng trước bán cho khách X giá bao nhiêu"?
