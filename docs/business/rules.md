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

### ✅ Đã xác nhận (2026-10-14)

- **BR-PRD-01:** Mỗi sản phẩm có mã, tên, danh mục, đơn vị tính, khối lượng tịnh mỗi đơn vị, và có thể có mô tả.
- **BR-PRD-02:** Mã sản phẩm do **hệ thống tự sinh** dạng `SP` + 6 chữ số (ví dụ `SP000001`), duy nhất toàn hệ thống, **không sửa được** sau khi tạo. Mã có thể nhảy số (do rollback) — chấp nhận.
- **BR-PRD-03:** Tên sản phẩm duy nhất, **không phân biệt hoa thường** ("Cám heo con 25kg" = "cám heo con 25kg").
- **BR-PRD-04:** Mỗi sản phẩm **bắt buộc** thuộc đúng một danh mục.
- **BR-PRD-05:** Mỗi sản phẩm có **một đơn vị giao dịch cố định**: `BAG` (bao) hoặc `KG`. Không quy đổi đơn vị khi giao dịch.
- **BR-PRD-06:** Mỗi sản phẩm có `net_weight_kg` > 0 (khối lượng một đơn vị; `KG` thì = 1). **Tấn chỉ là đơn vị báo cáo**: `tấn = số lượng × net_weight_kg / 1000` (ví dụ tổng nhập theo nhà cung cấp, tổng bán theo khách hàng).
- **BR-PRD-07:** Không lưu giá trong sản phẩm. Giá nhập/bán ghi trên từng dòng chứng từ.
- **BR-PRD-08:** Sản phẩm không xóa cứng; ngừng kinh doanh bằng `active = false`.
- **BR-PRD-09:** Không tạo mới sản phẩm trùng tên với sản phẩm **đã ngừng kinh doanh** (tránh một mặt hàng có hai mã, lịch sử bị tách đôi). Hệ thống từ chối kèm mã SP cũ và hướng dẫn **mở bán lại** (`PUT` với `active: true`). _(Xác nhận 2026-10-15)_

## Danh mục sản phẩm

### ✅ Đã xác nhận (2026-10-14)

- **BR-CAT-01:** Danh mục (ví dụ: cám gà, cám heo, cám cá) quản lý bằng bảng riêng, có CRUD.
- **BR-CAT-02:** Tên danh mục duy nhất, không phân biệt hoa thường.
- **BR-CAT-03:** Danh mục không xóa cứng; ngừng dùng bằng `active = false`.
- **BR-CAT-04:** Không tạo mới danh mục trùng tên với danh mục đã ngừng dùng; hệ thống hướng dẫn mở lại. _(Xác nhận 2026-10-15)_

---

## Kho

### ✅ Đã xác nhận (2026-10-16)

- **BR-WH-01:** Mỗi kho có mã, tên, loại kho (`CENTRAL` — tập kết, `RETAIL` — bán lẻ) và địa chỉ. Địa chỉ **bắt buộc**.
- **BR-WH-02:** Mã kho do **hệ thống tự sinh** dạng `KHO` + 3 chữ số (ví dụ `KHO001`), duy nhất, **không sửa được**. Mã có thể nhảy số (do rollback) — chấp nhận.
- **BR-WH-03:** Tên kho duy nhất, **không phân biệt hoa thường**.
- **BR-WH-04:** Kho không xóa cứng; ngừng hoạt động bằng `active = false`. Không tạo mới kho trùng tên với kho đã ngừng hoạt động; hệ thống hướng dẫn mở lại (giống BR-PRD-09).
- **BR-WH-05:** Kho còn tồn hàng không được ngừng hoạt động. _(Chưa thực thi được vì chưa có bảng `inventory` — bổ sung kiểm tra khi làm module tồn kho ở Tuần 2.)_

### Ngoài phạm vi MVP

- ~~BR-WH-Q01: Sức chứa tối đa của kho~~ — không quản lý trong MVP.
- ~~BR-WH-Q02: Vị trí trong kho (kệ, ô)~~ — không quản lý trong MVP.

---

## Nhà cung cấp

### ✅ Đã xác nhận (2026-10-16)

- **BR-SUP-01:** Mỗi NCC có mã, tên, **người liên hệ (bắt buộc)**; số điện thoại, email, địa chỉ **tùy chọn**. Email nếu nhập phải đúng định dạng.
- **BR-SUP-02:** Mã NCC do **hệ thống tự sinh** dạng `NCC` + 3 chữ số (ví dụ `NCC001`), duy nhất, **không sửa được**. Mã có thể nhảy số (do rollback) — chấp nhận.
- **BR-SUP-03:** Tên NCC duy nhất, **không phân biệt hoa thường**.
- **BR-SUP-04:** NCC không xóa cứng; ngừng hoạt động bằng `active = false`. Không tạo mới NCC trùng tên với NCC đã ngừng hoạt động; hệ thống hướng dẫn mở lại (giống BR-PRD-09).
- **BR-SUP-05:** Quản lý **NCC nào được cung cấp SP nào** bằng bảng nối `product_supplier` (nhiều–nhiều). Mỗi cặp (NCC, SP) chỉ có một dòng: gỡ liên kết = `active = false`, gán lại = bật `active = true` trên chính dòng đó.
- **BR-SUP-06:** Không gán SP **đã ngừng kinh doanh**, không gán SP cho NCC **đã ngừng hoạt động**.
- **BR-SUP-07:** Ngừng hoạt động NCC **giữ nguyên** các liên kết (mở lại NCC không phải gán lại).
- **BR-SUP-08:** **Chặn cứng** khi nhập kho: phiếu nhập từ NCC X chỉ được chứa SP đang liên kết `active = true` với X. _(Thực thi ở module nhập kho.)_
