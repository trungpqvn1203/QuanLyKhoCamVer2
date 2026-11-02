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

## Thay đổi: Đơn khách đặt — trạng thái Chờ xác nhận (2026-11-02, V25)

Bài toán: mỗi ngày có rất nhiều đơn đến qua điện thoại và Zalo, nhiều khách đặt trước cho cả tuần. Người nghe máy phải nhớ để lúc sau gõ phiếu, và sót đơn. Giải pháp: ghi đơn **ngay lúc nghe máy**, chỉ cần khách + SP + SL, chưa cần giá và kho. Đơn nằm trên hệ thống ở trạng thái **Chờ xác nhận** cho đến khi kho gọi lại khách để chốt.

- **Thêm trạng thái thay vì thêm module** — đã cân nhắc một bảng `customer_order` riêng ("đơn đặt" → bấm "lên phiếu" → phiếu bán). Cuối cùng chọn thêm trạng thái `PENDING` vào chính phiếu bán, vì quy trình chỉ có một đường: một đơn thành một phiếu, giao một lần. Chủ cửa hàng chỉ phải nghĩ về **một** thứ ("phiếu bán, có thêm bước chờ xác nhận") và làm việc trên **một** màn hình. Khi nào mới cần bảng riêng: một đơn giao nhiều chuyến (đặt 100 bao, giao 2 lần), hoặc phải lưu lịch sử từng lần khách sửa đơn.
- **Nới ràng buộc có điều kiện, không bỏ ràng buộc** — phiếu `PENDING` chưa có kho, nhưng không vì vậy mà bỏ "phiếu bán phải có kho". `ck_stock_document_warehouse` dùng `CASE`: công nợ đầu kỳ thì không có kho; phiếu bán `PENDING`, hoặc `CANCELLED` mà `confirmed_at IS NULL` (hủy khi chưa từng xác nhận), thì được trống; **mọi trường hợp khác bắt buộc có kho**. Ví dụ PX000123 vừa ghi lúc 7h sáng: kho trống thì hợp lệ. Lúc xác nhận, nếu code lỡ quên gán kho, DB từ chối chuyển sang `DRAFT`. Test `dbShouldRequireWarehouseFromDraft` chứng minh điều đó.
- **Đơn chờ xác nhận chưa có dòng phiếu** — phần khách đặt nằm ở bảng riêng `sale_request_item` (SP + SL, không giá). Nhờ vậy không phải nới `unit_price NOT NULL` hay bất kỳ CHECK nào của `stock_document_item`, bảng mà việc trừ tồn và tính tiền dựa vào. Vùng "chưa chắc chắn" tách hẳn khỏi vùng "đã chốt".
- **Vì sao không dùng cột `ordered_quantity` có sẵn** — dòng phiếu đã có `ordered_quantity` (SL đặt NCC của phiếu nhập), dùng lại thì gọn hơn. Nhưng chủ cửa hàng muốn **đổi được cả SP** (hết cám 551, khách đồng ý lấy 552). `ordered_quantity` gắn với dòng của SP *mới*, nên mất dấu khách ban đầu đặt cám 551. Bảng riêng giữ nguyên "khách đặt 50 bao 551" bên cạnh "thực giao 40 bao 552". Bảng này bị khóa từ lúc xác nhận, thành bằng chứng khi khách hỏi "sao tôi đặt 50 mà giao 40".
- **Một hàm khóa, nhiều trạng thái được phép** — `lockForChange(id, Set.of(PENDING, DRAFT), "hủy")`: vẫn khóa trước, kiểm tra trạng thái sau như BR-IMP-14, chỉ khác là tập trạng thái hợp lệ có hai phần tử. Thông báo lỗi ghép nhãn theo thứ tự vòng đời ("Chỉ phiếu Chờ xác nhận hoặc Nháp mới được hủy") nhờ `sorted()` trên enum, vốn sắp theo thứ tự khai báo.
- **Không đổi khách sau khi đã ghi đơn** — sửa đơn hay xác nhận mà gửi `customerId` khác thì nhận 400. Phần khách đặt thuộc về một người mua cụ thể; đổi khách là sai nghĩa của bằng chứng đó. Ghi nhầm khách thì hủy đơn (bắt buộc lý do) rồi ghi lại.
- **Frontend: một khung, ba chế độ** — `SalesOrderDrawer` nay có: form khách đặt (không giá, không kho), bước xác nhận (form phiếu bán điền sẵn từ phần khách đặt + giá riêng; nếu chỉ có một kho thì chọn sẵn), và phiếu nháp như cũ. Bước xác nhận chỉ là **trạng thái của giao diện** (`confirmValues`), chưa gọi API cho tới lúc bấm "Xác nhận đơn". Bấm "Quay lại đơn" là bỏ nháp xác nhận, phiếu trên server vẫn là Chờ xác nhận. Hàm điền sẵn `confirmationFormValues` là hàm thuần, nên test được bằng vitest mà không cần render.

### Lỗi đã gặp

- **Test chèn SQL tay bị ràng buộc mới chặn** — `SalesPersistenceTest` và `CashBookApiTest` chèn phiếu bán bằng `INSERT` mà không có `order_channel`, nên vi phạm `ck_stock_document_order_channel`. Ràng buộc đã làm đúng việc của nó: mọi phiếu bán phải có kênh. Cách sửa là thêm `'DIRECT'` vào các câu chèn đó, không phải nới ràng buộc. Trước khi thêm CHECK "bắt buộc" vào bảng đang có dữ liệu, cần `grep` mọi chỗ ghi thẳng vào bảng (test, seed). Migration cũng phải `UPDATE` dữ liệu cũ **trước** khi `ADD CONSTRAINT`.
- **Hibernate giữ bản cũ sau khi test sửa DB bằng `jdbcTemplate`** — test "SP ngừng kinh doanh thì từ chối ghi đơn" nhận 201 thay vì 409. Nguyên nhân: SP được tạo bằng repository trong cùng transaction test, nên `findAllById` trả lại entity trong persistence context, vẫn `active = true`. Cách sửa: `flush()` → `UPDATE` → `entityManager.clear()`. Cùng gốc với lỗi `@Modifying` ở trên.
- **Kỳ vọng sai trong test đồng thời** — test "xác nhận và hủy cùng lúc thì đúng 1 thành công" fail với 2 thành công. Hành vi này **đúng**: xác nhận chạy trước thì phiếu thành Nháp, mà Nháp thì hủy được. Viết lại test theo hai kết cục hợp lệ: hủy trước thì xác nhận bị từ chối và phiếu không có dòng; xác nhận trước thì hủy áp lên phiếu Nháp đủ dòng. Bài học: với test đồng thời, liệt kê **mọi thứ tự thực thi có thể** rồi kiểm "không có kết cục nào sai", đừng giả định chỉ một bên thắng.
- **`npx tsc --noEmit` không kiểm tra gì** — `tsconfig.json` gốc có `"files": []` và chỉ trỏ sang `tsconfig.app.json` / `tsconfig.node.json`. Chạy `tsc --noEmit` ở gốc nên luôn im lặng, kể cả khi code có lỗi kiểu. Phải chạy `tsc --noEmit -p tsconfig.app.json` (hoặc `tsc -b` như lệnh build) mới bắt được lỗi fixture của test in phiếu thiếu `orderChannel`.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao `ck_stock_document_warehouse` cho phép `CANCELLED` không có kho **chỉ khi** `confirmed_at IS NULL`? Bỏ điều kiện đó thì lỗ hổng nào mở ra?
2. Nếu sau này một đơn được giao làm hai chuyến, thiết kế "trạng thái trên phiếu bán" vỡ ở đâu, và bảng `customer_order` riêng giải quyết thế nào?
3. Hai người cùng bấm "Xác nhận đơn" cho PX000123: lần lượt điều gì xảy ra ở DB, và vì sao không có dòng phiếu nào bị chép hai lần?
