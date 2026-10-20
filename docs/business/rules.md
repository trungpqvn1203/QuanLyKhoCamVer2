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

### ✅ Đã xác nhận (2026-10-19)

- **BR-INV-07:** Số lượng tồn và số lượng trên chứng từ **luôn là số nguyên** (cả `BAG` lẫn `KG`). _(Trả lời BR-INV-Q02)_

### ❓ Câu hỏi mở

- **BR-INV-Q01:** Có cần quản lý lô hàng (batch/lot) không? Ví dụ: ngày sản xuất, hạn sử dụng theo từng lô.

---

## Xem tồn kho theo sản phẩm

### ✅ Đã xác nhận (2026-10-19)

- **BR-STK-01:** Tổng tồn của một SP = cộng tồn ở **mọi kho, kể cả kho đã ngừng hoạt động** (hàng vẫn nằm đó cho đến khi điều chuyển đi). Phần chi tiết theo kho ghi rõ kho nào đã ngừng.
- **BR-STK-02:** Hiển thị kèm số tấn: `tấn = số lượng × net_weight_kg / 1000`, làm tròn `HALF_UP`, 3 chữ số lẻ (tức đến kg). Chỉ để xem/báo cáo, không dùng trong giao dịch.
- **BR-STK-03:** Mỗi SP có thể đặt **một mức tồn tối thiểu** (`min_stock_quantity`, số nguyên ≥ 0, tùy chọn), so với tổng tồn mọi kho. Không đặt = không cảnh báo Sắp hết.
- **BR-STK-04:** Tình trạng tồn, luôn tính trên **tổng** mọi kho: **Hết hàng** khi tổng = 0; **Sắp hết** khi 0 < tổng **≤** mức tối thiểu; còn lại là **Còn hàng**.
- **BR-STK-05:** Chọn một kho ở trang Sản phẩm: cột tồn hiện số **tại kho đó** kèm tổng mọi kho. Không bớt SP nào khỏi danh sách, và tình trạng tồn vẫn tính theo tổng (theo BR-STK-04).
- **BR-STK-06:** **Đang về** = tổng SL đặt trên các phiếu nhập còn **Nháp**. Hiện riêng, **không cộng vào tồn**. Phiếu Hoàn tất (đã vào tồn) và phiếu Đã hủy không tính.

---

## Tiền tệ

### ✅ Đã xác nhận

- **BR-MON-01:** Không sử dụng `float` hoặc `double` cho giá tiền. Java dùng `BigDecimal`, PostgreSQL dùng `NUMERIC`.
- **BR-MON-02:** Quy tắc làm tròn phải được xác nhận trước khi triển khai, không tự chọn.

### ✅ Đã xác nhận (2026-10-19)

- **BR-MON-03:** Chỉ dùng **VND**. _(Trả lời BR-MON-Q03)_
- **BR-MON-04:** Đơn giá lưu **2 chữ số thập phân** (`NUMERIC(15,2)`). _(Trả lời BR-MON-Q01)_
- **BR-MON-05:** Thành tiền dòng = số lượng × đơn giá, **làm tròn `HALF_UP` về đồng** (0 chữ số thập phân). Tổng phiếu = tổng thành tiền các dòng, không làm tròn thêm. _(Trả lời BR-MON-Q02)_

---

## Nhập kho

### ✅ Đã xác nhận (2026-10-19)

- **BR-IMP-01:** Phiếu nhập ghi nhà cung cấp, kho nhận, ngày chứng từ và nhiều dòng hàng. Mỗi dòng có sản phẩm, số lượng và đơn giá nhập.
- **BR-IMP-02:** Phiếu có trạng thái **Nháp → Hoàn tất**, vì số lượng NCC giao thực tế có thể lệch so với lúc lên đơn. Phiếu nháp **không ảnh hưởng tồn** và được sửa tự do. _(Trả lời BR-IMP-Q01)_
- **BR-IMP-03:** Mỗi dòng lưu **cả SL đặt và SL thực nhận**. SL thực nhận mặc định bằng SL đặt và được sửa khi hàng về. Khi hoàn tất, tồn kho tăng theo **SL thực nhận**, và thành tiền cũng tính theo SL thực nhận.
- **BR-IMP-04:** Hoàn tất phiếu: tăng tồn và ghi `StockTransaction` cho mọi dòng **trong cùng một transaction**. Sau khi hoàn tất, phiếu bị khóa, không sửa được.
- **BR-IMP-05:** Phiếu nháp không dùng nữa chuyển sang **Đã hủy**. Không xóa cứng, mã phiếu được giữ lại.
- **BR-IMP-06:** Phiếu đã hoàn tất mà phát hiện sai thì **đảo toàn phiếu**: chuyển sang **Đã đảo**, ghi `StockTransaction` OUT tham chiếu giao dịch gốc. Nếu tồn hiện tại không đủ để trừ thì từ chối. Sau đó tạo phiếu đúng mới. Điều chỉnh từng dòng chưa hỗ trợ trong MVP.
- **BR-IMP-07:** Mã phiếu do hệ thống tự sinh dạng `PN` + 6 chữ số (ví dụ `PN000001`), duy nhất, không sửa được. Mã có thể nhảy số, chấp nhận như BR-PRD-02.
- **BR-IMP-08:** Ngày chứng từ mặc định là hôm nay. Được ghi lùi ngày (nhập bù phiếu giấy), **không được ghi ngày tương lai**. Thời điểm ghi sổ thật lưu riêng.
- **BR-IMP-09:** Ghi nhận giá nhập trên từng dòng. **Chưa tính giá vốn** trong MVP. _(Trả lời BR-IMP-Q02)_
- **BR-IMP-10:** Áp dụng BR-SUP-08 (chỉ nhập SP đang liên kết với NCC). Kiểm tra cả khi lưu nháp **và lúc hoàn tất**, vì liên kết có thể bị gỡ trong khoảng giữa hai lần.

**Bảo vệ trạng thái ở tầng service** _(xác nhận 2026-10-19)_

- **BR-IMP-11:** Chỉ phiếu **Nháp** mới được sửa (kể cả dòng hàng), hủy hoặc hoàn tất. Chỉ có ba bước chuyển hợp lệ: Nháp → Hoàn tất, Nháp → Đã hủy, Hoàn tất → Đã đảo. Đã hủy và Đã đảo là trạng thái cuối.
- **BR-IMP-12:** Chỉ phiếu **Hoàn tất** mới được đảo, và mỗi phiếu **chỉ được đảo một lần**.
- **BR-IMP-13:** Không xóa phiếu ở bất kỳ trạng thái nào (API không có DELETE). Không sửa dòng hàng của phiếu đã hoàn tất.
- **BR-IMP-14:** Mọi thao tác thay đổi phiếu phải **khóa dòng phiếu (`SELECT … FOR UPDATE`) rồi mới kiểm tra lại trạng thái, bên trong cùng transaction**. Khóa chỉ có tác dụng khi *mọi* đường thay đổi trạng thái đều theo quy trình này. Thêm thao tác mới mà đọc phiếu bằng `findById` rồi sửa là mở lại lỗ hổng.

### ⚠️ Giả định tạm thời

- **BR-IMP-A04:** Kho nhận, NCC và sản phẩm phải đang hoạt động, cả khi lưu nháp lẫn khi hoàn tất.
- **BR-IMP-A05:** Một sản phẩm chỉ xuất hiện **một dòng** trong mỗi phiếu.
- **BR-IMP-A06:** SL đặt > 0. SL thực nhận ≥ 0 (bằng 0 nghĩa là NCC không giao dòng đó). Phải có ít nhất một dòng có SL thực nhận > 0 thì mới hoàn tất được.
- **BR-IMP-A07:** Đơn giá ≥ 0. Giá 0 dùng cho hàng tặng hoặc khuyến mãi.
- **BR-IMP-A08:** Không quản lý lô hàng trong MVP (BR-INV-Q01 tạm trả lời "không").

---

## Bán hàng / Xuất kho

### ✅ Đã xác nhận (2026-10-20)

_(Trả lời BR-SAL-Q01: có công nợ, chỉ ở mức phiếu bán + lịch sử thanh toán. BR-SAL-Q02: giá nhập tay, có bảng giá riêng theo **từng khách**. BR-SAL-Q03: đảo toàn phiếu khi giao sai / hàng không đạt.)_

**Phiếu bán**

- **BR-SAL-01:** Phiếu bán ghi **khách hàng (bắt buộc — để xác minh ai mua, ai nợ)**, kho xuất, ngày chứng từ và nhiều dòng hàng. Mỗi dòng có SP, số lượng **> 0** (nguyên) và đơn giá bán. Mỗi SP một dòng.
- **BR-SAL-02:** Trạng thái **Nháp → Hoàn tất**. Nháp **không ảnh hưởng tồn và không giữ chỗ tồn** (lập nháp vượt tồn vẫn được). Hoàn tất: trừ tồn và ghi `StockTransaction` OUT cho mọi dòng **trong một transaction**; một dòng thiếu tồn → từ chối, rollback toàn bộ, phiếu vẫn là Nháp.
- **BR-SAL-03:** Mã phiếu tự sinh `PX` + 6 chữ số (`PX000001`), có thể nhảy số.
- **BR-SAL-04:** Áp dụng như phiếu nhập: ngày chứng từ mặc định hôm nay, lùi được, không ở tương lai (BR-IMP-08); chỉ Nháp được sửa/hủy/hoàn tất, chỉ Hoàn tất được đảo (BR-IMP-11, 12); không xóa phiếu (BR-IMP-13); mọi thao tác thay đổi phiếu — **kể cả thu tiền** — khóa phiếu `FOR UPDATE` rồi mới kiểm tra trạng thái (BR-IMP-14).
- **BR-SAL-05:** Giao sai hoặc hàng không đạt → **đảo toàn phiếu** (lý do bắt buộc): Đã đảo, mỗi OUT gốc có một IN trỏ về nó, hàng về lại đúng kho đã xuất. Mỗi phiếu đảo một lần. **Chưa hỗ trợ trả một phần.**
- **BR-SAL-15:** Kho, khách, SP phải đang hoạt động, kiểm tra cả khi lưu nháp lẫn khi hoàn tất.

**Giá bán**

- **BR-SAL-06:** Mỗi khách có thể có **bảng giá riêng**: một giá hiện hành cho mỗi SP (≥ 0, 2 chữ số lẻ). Không lưu lịch sử giá trong bảng giá.
- **BR-SAL-07:** Khi thêm dòng vào phiếu, giá **điền sẵn** từ bảng giá của khách (nếu có); người bán **sửa tay được**. Không có giá riêng → nhập tay.
- **BR-SAL-08:** Giá thực bán **chụp lại trên dòng phiếu**. Đổi/xóa bảng giá không làm đổi phiếu đã lập. Backend không ép giá dòng phải bằng bảng giá. (BR-PRD-07 vẫn đúng: giá không lưu trong SP.)

**Thanh toán & công nợ**

- **BR-SAL-09:** Chỉ phiếu **Hoàn tất** mới thu tiền (khách trả **sau khi nhận hàng**). Lúc hoàn tất không có ô "trả ngay".
- **BR-SAL-10:** Còn nợ của phiếu = tổng phiếu − đã thu. Công nợ của khách = tổng còn nợ các phiếu Hoàn tất. Không có số dư nợ lưu riêng ở khách, không có phiếu thu gộp nhiều phiếu.
- **BR-SAL-11:** Một lần thu **không được vượt số còn nợ** của phiếu.
- **BR-SAL-12:** Một lần thu có thể **kết hợp tiền mặt + chuyển khoản**; mỗi hình thức có số tiền > 0 thành một dòng riêng, ghi cùng transaction. Tiền tính đến đồng. Ngày thu mặc định hôm nay, **chọn tự do** — được ghi trước ngày bán hoặc ngày tương lai _(đổi 2026-10-20 theo yêu cầu; trước đó chặn cả hai)_.
- **BR-SAL-13:** Lịch sử thanh toán **bất biến**. Thu nhầm → **hoàn** toàn bộ khoản đó (lý do bắt buộc) bằng một dòng hoàn trỏ về khoản gốc; mỗi khoản hoàn một lần.
- **BR-SAL-14:** Phiếu **đã thu tiền** (đã thu > 0) **không được đảo** — phải hoàn hết các khoản thu trước. Trường hợp khách trả trước rồi trả hàng: để sau.

### ❓ Câu hỏi mở

- **BR-SAL-Q04:** Trả hàng một phần (vài bao trong phiếu) — khi nào cần?
- **BR-SAL-Q05:** Khách trả trước / tiền thừa — có cần số dư có của khách không?
- **BR-SAL-Q06:** Có chặn ngừng hoạt động khách đang còn nợ không? (Hiện: không chặn, vẫn thu nợ được.)

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

---

## Khách hàng

### ✅ Đã xác nhận (2026-10-20)

- **BR-CUS-01:** Mỗi khách có mã, tên, loại, **số điện thoại (bắt buộc)**; người liên hệ, địa chỉ, ghi chú **tùy chọn**.
- **BR-CUS-02:** Mã khách do **hệ thống tự sinh** dạng `KH` + 4 chữ số (`KH0001`), duy nhất, **không sửa được**. Có thể nhảy số (rollback) — chấp nhận. Quá 9999 thì mã dài thêm (`KH10000`), vẫn duy nhất — _đúng từ V10; trước đó `lpad` cắt cụt mã, xem LESSONS 2026-10-20_.
- **BR-CUS-03:** Loại khách: Đại lý (`DEALER`), Trang trại (`FARM`), Khách lẻ (`RETAIL`).
- **BR-CUS-04:** **SĐT là định danh chống trùng**, duy nhất trên toàn bộ khách (kể cả đã ngừng). **Tên được phép trùng** (hai "Trại heo Minh" ở hai xã).
- **BR-CUS-05:** Khách không xóa cứng; ngừng hoạt động bằng `active = false`. Tạo khách mới bằng SĐT của khách đã ngừng → từ chối, hướng dẫn mở lại (giống BR-SUP-04).
- **BR-CUS-06:** SĐT được **chuẩn hóa trước khi lưu và trước khi kiểm trùng**: bỏ khoảng trắng, dấu chấm, gạch ngang; kết quả phải là `0` + 9–10 chữ số (`0901 234 567` → `0901234567`). Chưa nhận `+84`. DB có CHECK chỉ cho lưu dạng đã chuẩn hóa.
- Công nợ và giá riêng theo khách: xem BR-SAL-06..14 ở module bán hàng.

---

## Nhân viên, chấm công, bảng lương

> Bổ sung ngoài phạm vi MVP ban đầu theo yêu cầu (2026-10-20). Quản lý nhập công hộ, chưa có đăng nhập.

### ✅ Đã xác nhận (2026-10-20)

- **BR-EMP-01:** Mã nhân viên `NV` + 3 chữ số (`NV001`), tự sinh, không sửa được. Không xóa cứng: nghỉ việc = `active = false`; chấm công, bảng lương cũ vẫn giữ.
- **BR-EMP-02:** Mỗi nhân viên có **lương ngày công**, **đơn giá tấn bốc vác**, hoặc **cả hai** (ít nhất một), mỗi đơn giá ≥ 0, 2 chữ số lẻ (BR-MON-04). Để trống = không hưởng theo cách đó.
- **BR-EMP-03:** Tên được trùng. SĐT tùy chọn, **không** chặn trùng; có nhập thì chuẩn hóa như BR-CUS-06.
- **BR-ATT-01:** Mỗi nhân viên mỗi ngày một dòng chấm công: trạng thái ngày Cả ngày (1 công) / Nửa ngày (0,5) / Nghỉ (0) + số tấn bốc vác (≥ 0, tối đa 3 chữ số lẻ). Quản lý nhập tay.
- **BR-ATT-02:** Ngày Nghỉ thì số tấn = 0. Không chấm công ngày tương lai (giờ VN). Không chấm công cho nhân viên đã nghỉ.
- **BR-PAY-01:** Lương tháng = tổng công × lương ngày + tổng tấn × đơn giá tấn; **mỗi phần làm tròn `HALF_UP` về đồng**, tổng = tổng hai phần đã làm tròn. Ví dụ 41,235 tấn × 52.500 = 2.164.837,5 → 2.164.838.
- **BR-PAY-02:** Chưa chốt: bảng lương tính trực tiếp từ chấm công với đơn giá hiện tại. **Chốt lương** lưu bản chụp (công, tấn, **đơn giá lúc chốt**) — đổi đơn giá sau không đổi lương đã chốt.
- **BR-PAY-03:** Tháng đã chốt thì **khóa chấm công** của tháng đó. Sửa = **Hủy chốt** (bắt buộc lý do) → bảng lương cũ giữ lại với trạng thái Đã hủy, tháng mở lại để sửa và chốt lại. Mỗi tháng chỉ một bảng lương còn hiệu lực.
