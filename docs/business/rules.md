# Quy tắc nghiệp vụ

Phân biệt 3 loại:
- ✅ **Đã xác nhận** — Quy tắc cứng, đã được đồng ý
- ⚠️ **Giả định tạm thời** — Hợp lý nhưng chưa xác nhận chính thức
- ❓ **Câu hỏi mở** — Cần làm rõ trước khi triển khai

---

## Tồn kho

### ✅ Đã xác nhận

- **BR-INV-01:** Số lượng tồn kho không được âm. Hệ thống phải từ chối xuất kho nếu số lượng xuất vượt quá tồn khả dụng.
- **BR-INV-02:** Kiểm tra tồn và trừ tồn phải nằm trong cùng một database transaction để tránh race condition.
- **BR-INV-03:** Database phải có ràng buộc CHECK ngăn tồn kho âm, không chỉ dựa vào kiểm tra trong code Java.
- **BR-INV-04:** Mỗi biến động tồn kho phải tạo bản ghi `StockTransaction` tương ứng.
- **BR-INV-05:** Bản ghi `StockTransaction` đã ghi nhận không được sửa hoặc xóa.
- **BR-INV-06:** Sai sót được xử lý bằng giao dịch đảo hoặc giao dịch điều chỉnh, có tham chiếu đến giao dịch gốc.

### ⚠️ Giả định tạm thời

- **BR-INV-A01:** Tồn kho được quản lý theo cặp (sản phẩm, kho). Mỗi sản phẩm có một bản ghi tồn tại mỗi kho mà nó xuất hiện.
- **BR-INV-A02:** "Tồn khả dụng" trong MVP bằng tồn hiện tại (chưa có cơ chế đặt trước / reserve).

### ❓ Câu hỏi mở

- **BR-INV-Q01:** Có cần quản lý lô hàng (batch/lot) không? Ví dụ: ngày sản xuất, hạn sử dụng theo từng lô.
- **BR-INV-Q02:** Số lượng tồn có thể là số thập phân không? (ví dụ: 2.5 bao 25kg)

---

## Tiền tệ

### ✅ Đã xác nhận

- **BR-MON-01:** Không sử dụng `float` hoặc `double` cho giá tiền. Java dùng `BigDecimal`, PostgreSQL dùng `NUMERIC`.
- **BR-MON-02:** Quy tắc làm tròn phải được xác nhận trước khi triển khai, không tự chọn.

### ❓ Câu hỏi mở

- **BR-MON-Q01:** Độ chính xác giá tiền: NUMERIC(15,2) hay NUMERIC(15,4)? Có cần giá lẻ dưới đồng không?
- **BR-MON-Q02:** Quy tắc làm tròn khi tính thành tiền: HALF_UP, HALF_EVEN, hay FLOOR?
- **BR-MON-Q03:** Đơn vị tiền tệ: chỉ VND hay cần hỗ trợ nhiều đơn vị?

---

## Nhập kho

### ⚠️ Giả định tạm thời

- **BR-IMP-A01:** Một phiếu nhập có thể chứa nhiều dòng hàng (nhiều sản phẩm, mỗi dòng có số lượng và đơn giá).
- **BR-IMP-A02:** Phiếu nhập kho ghi nhận nhà cung cấp, kho nhận, ngày nhập và danh sách hàng hóa.
- **BR-IMP-A03:** Phiếu nhập đã hoàn tất không được sửa nội dung để tránh sai lệch lịch sử. Nếu sai, tạo phiếu điều chỉnh.

### ❓ Câu hỏi mở

- **BR-IMP-Q01:** Phiếu nhập có cần trạng thái (nháp → xác nhận → hoàn tất) hay nhập là hoàn tất luôn?
- **BR-IMP-Q02:** Có cần ghi nhận giá nhập để tính giá vốn không?

---

## Bán hàng / Xuất kho

### ⚠️ Giả định tạm thời

- **BR-SAL-A01:** Một phiếu bán/xuất có thể chứa nhiều dòng hàng.
- **BR-SAL-A02:** Bán hàng tạo phiếu bán kèm thông tin khách hàng (đại lý hoặc trang trại), kho xuất, ngày bán.
- **BR-SAL-A03:** Khi bán hàng thành công, tồn kho tại kho xuất giảm tương ứng.

### ❓ Câu hỏi mở

- **BR-SAL-Q01:** Có cần quản lý công nợ khách hàng không? Hay MVP chỉ ghi nhận đã bán?
- **BR-SAL-Q02:** Giá bán có khác nhau theo từng kho hoặc từng loại khách không?
- **BR-SAL-Q03:** Có cho phép trả hàng không? Nếu có, quy trình như thế nào?

---

## Điều chuyển kho

### ✅ Đã xác nhận

- **BR-TRF-01:** Một điều chuyển thành công phải: kho nguồn giảm, kho đích tăng đúng số lượng, lịch sử đủ, trong cùng transaction.
- **BR-TRF-02:** Nếu bất kỳ bước nào thất bại, toàn bộ transaction rollback.
- **BR-TRF-03:** Không được có trạng thái "hoàn tất" mà chỉ một kho được cập nhật.
- **BR-TRF-04:** Xử lý thứ tự khóa (lock ordering) để giảm nguy cơ deadlock khi có nhiều giao dịch đồng thời.

### ⚠️ Giả định tạm thời

- **BR-TRF-A01:** Điều chuyển diễn ra tức thời (không có trạng thái "đang vận chuyển" trong MVP).
- **BR-TRF-A02:** Chỉ điều chuyển giữa các kho thuộc hệ thống.

### ❓ Câu hỏi mở

- **BR-TRF-Q01:** Có kho nào bị hạn chế không được điều chuyển đến/từ không?
- **BR-TRF-Q02:** Cần trạng thái phức tạp hơn cho điều chuyển không? (yêu cầu → duyệt → xuất → nhận)

---

## Sản phẩm

### ⚠️ Giả định tạm thời

- **BR-PRD-A01:** Mỗi sản phẩm có mã, tên, đơn vị tính, và có thể có mô tả.
- **BR-PRD-A02:** Sản phẩm đang có tồn kho hoặc đã xuất hiện trong giao dịch không được xóa cứng (soft delete hoặc đánh dấu ngừng kinh doanh).
- **BR-PRD-A03:** Mã sản phẩm là duy nhất trong toàn hệ thống.

### ❓ Câu hỏi mở

- **BR-PRD-Q01:** Sản phẩm có phân nhóm/danh mục không? (ví dụ: cám gà, cám heo, cám cá)
- **BR-PRD-Q02:** Đơn vị tính có cố định theo sản phẩm hay có thể chuyển đổi? (ví dụ: 1 bao = 25kg)

---

## Kho

### ⚠️ Giả định tạm thời

- **BR-WH-A01:** Mỗi kho có mã, tên, địa chỉ, loại kho (tập kết hoặc bán lẻ).
- **BR-WH-A02:** Kho đang chứa hàng không được xóa.

### ❓ Câu hỏi mở

- **BR-WH-Q01:** Kho có sức chứa tối đa không?
- **BR-WH-Q02:** Có cần quản lý vị trí trong kho (kệ, ô) không?
