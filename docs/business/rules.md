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

- **BR-INV-07:** Số lượng tồn và số lượng trên chứng từ **luôn là số nguyên** (với mọi đơn vị: bao, kg, túi…). _(Trả lời BR-INV-Q02)_

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

**Đơn khách đặt — Chờ xác nhận** _(✅ duyệt 2026-11-02, migration V25)_

> Mục tiêu: không sót đơn khi khách gọi điện / nhắn Zalo nhiều; sau này app khách tạo đơn ở cùng trạng thái này.
> Đơn đặt **là phiếu bán** ở trạng thái mới, không phải module riêng (đã cân nhắc bảng đơn đặt riêng — chỉ cần khi một đơn giao nhiều chuyến hoặc phải lưu lịch sử từng lần sửa).

- **BR-SAL-16:** Phiếu bán có thêm trạng thái **Chờ xác nhận** (trước Nháp). Bước chuyển: **Chờ xác nhận → Nháp** (xác nhận), **Chờ xác nhận → Đã hủy**. Kho lập phiếu trực tiếp vẫn bắt đầu từ Nháp như cũ. BR-SAL-01 (bắt buộc kho, đơn giá) áp dụng **từ Nháp trở đi**.
- **BR-SAL-17:** Đơn chờ xác nhận ghi: khách (đang hoạt động), **SP + SL khách đặt** (SL nguyên > 0, theo **đơn vị tính của SP**, mỗi SP một dòng, SP đang kinh doanh), **ngày muốn giao** (tùy chọn, được ở tương lai — khác ngày bán BR-SAL-04), **kênh** (Trực tiếp / Điện thoại / Zalo; "App" để dành cho app khách), ghi chú, người ghi. **Không có giá, không có kho, không ảnh hưởng tồn.** Có mã `PX` ngay để báo khách. Ngày bán mặc định = ngày ghi đơn.
- **BR-SAL-18:** Chỉ đơn **Chờ xác nhận** được sửa phần khách đặt (SP, SL, ngày muốn giao, kênh, ghi chú). Không đổi khách — ghi nhầm khách thì hủy và ghi lại.
- **BR-SAL-19:** **Xác nhận** (khóa phiếu `FOR UPDATE` như BR-SAL-04): kho chọn kho xuất, ngày bán, dòng hàng có giá — điền sẵn từ phần khách đặt + bảng giá riêng (BR-SAL-07), **được đổi SL, đổi SP** (hết loại này tư vấn loại khác), thêm / bớt dòng. Áp dụng BR-SAL-01, 04, 15. Phiếu thành **Nháp**, ghi người và lúc xác nhận. **Chưa trừ tồn** — trừ tồn khi Hoàn tất như BR-SAL-02.
- **BR-SAL-20:** Phần **khách đặt được giữ nguyên** sau khi xác nhận (không sửa được nữa) để đối chiếu "khách đặt" với "thực giao" — kể cả khi phiếu bị hủy hoặc đảo.
- **BR-SAL-21:** Hủy đơn chờ xác nhận **bắt buộc lý do** (vd. "khách đổi ý", "hết hàng, khách không lấy loại khác") — khác hủy phiếu nháp (lý do tùy chọn). Hai người cùng xác nhận / hủy một đơn → khóa phiếu, người sau bị từ chối vì trạng thái đã đổi.
- **BR-SAL-22:** Đơn chờ xác nhận **không tính** vào doanh số (BR-RPT-02), công nợ, "số phiếu bán nháp"; Tổng quan có thẻ riêng **"Đơn chờ xác nhận"**.

**Điều chỉnh giá phiếu đã giao, chưa thu tiền** _(✅ duyệt 2026-11-03, migration V26 — ngoại lệ có điều kiện của BR-SAL-04 "phiếu Hoàn tất bị khóa")_

> Tình huống: giao hàng xong khách kêu đắt, chủ cửa hàng bớt giá. Trước đây phải đảo phiếu (hàng về lại kho trên sổ) rồi lập phiếu mới.

- **BR-SAL-23:** Phiếu **Hoàn tất** có **đã thu = 0** được **điều chỉnh giá**: chỉ đổi đơn giá các dòng. Số lượng, SP, kho **không đổi** → tồn kho và `StockTransaction` không bị động tới.
- **BR-SAL-24:** Bắt buộc **lý do**. Phải có **ít nhất một dòng đổi giá** (gửi giá y như cũ → từ chối). Giá mới ≥ 0, tối đa 2 chữ số lẻ (BR-SAL-06); thành tiền dòng và tổng phiếu tính lại theo BR-MON-05; công nợ khách và doanh số theo ngày bán (BR-RPT-02) đổi theo. Chỉ gửi các dòng cần đổi; SP không có trên phiếu → 400.
- **BR-SAL-25:** Khách **đã trả từ 1 đồng** → không điều chỉnh được nữa (cùng tinh thần BR-SAL-14). Phiếu Nháp sửa giá bằng cách sửa phiếu như cũ; Đã hủy / Đã đảo → từ chối. Khóa phiếu `FOR UPDATE` trước khi kiểm tra (BR-SAL-04).
- **BR-SAL-26:** Mỗi dòng đổi giá ghi **nhật ký bất biến**: giá cũ → mới, thành tiền cũ → mới, lý do, người sửa, thời điểm. Màn hình phiếu có mục "Lịch sử điều chỉnh giá"; in lại phiếu và app khách thấy giá mới. Sổ quỹ không đổi (chưa thu đồng nào).

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

## Công nợ nhà cung cấp

> Thiết kế: migration V16. Cùng mẫu với công nợ khách (BR-SAL-09..14), chiều tiền ngược lại: mình trả cho NCC.

### ✅ Đã xác nhận (2026-10-25)

- **BR-SPAY-01:** Chỉ phiếu nhập **Hoàn tất** mới trả tiền. Nợ của phiếu = tổng phiếu (theo SL thực nhận, BR-IMP-03) − đã trả. Công nợ NCC = tổng nợ các phiếu Hoàn tất còn nợ. Trả **theo từng phiếu nhập**, không trả gộp theo NCC; không có số dư nợ lưu riêng.
- **BR-SPAY-02:** Một lần trả không vượt số còn nợ của phiếu. Trả kết hợp tiền mặt + chuyển khoản được (mỗi hình thức một dòng). Tiền tính đến đồng. Ngày trả mặc định hôm nay, chọn tự do. Hoàn tất phiếu **không** có ô trả ngay — trả ngay thì bấm "Trả tiền" sau khi hoàn tất.
- **BR-SPAY-03:** Lịch sử trả tiền **bất biến**. Trả nhầm → **hoàn** toàn bộ khoản đó (lý do bắt buộc) bằng một dòng hoàn trỏ về khoản gốc; mỗi khoản hoàn một lần.
- **BR-SPAY-04:** Phiếu nhập **đã trả tiền** (đã trả > 0) **không được đảo** — phải hoàn hết các khoản trả trước.
- **BR-SPAY-05:** Phiếu nhập đã hoàn tất (hoặc đã đảo) **trước khi có module này** coi như đã trả đủ: không hiện ở công nợ, không ghi trả tiền được, vẫn đảo được như trước. Phiếu nháp cũ hoàn tất sau này vẫn phát sinh nợ bình thường.
- **BR-SPAY-06:** Ai được trả tiền NCC và xem công nợ NCC: **Quản lý kho + Quản lý hệ thống** (như phiếu nhập).

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
- **BR-PRD-05:** Mỗi sản phẩm có **một đơn vị giao dịch cố định**, chọn từ danh mục đơn vị tính (BR-UNIT). Không quy đổi đơn vị khi giao dịch. _(Sửa 2026-10-28: trước đây chỉ `BAG` hoặc `KG`)_
- **BR-PRD-06:** Mỗi sản phẩm có `net_weight_kg` > 0 (khối lượng một đơn vị, luôn bắt buộc; đơn vị Kg thì = 1, BR-UNIT-03). **Tấn chỉ là đơn vị báo cáo**: `tấn = số lượng × net_weight_kg / 1000` (ví dụ tổng nhập theo nhà cung cấp, tổng bán theo khách hàng).
- **BR-PRD-07:** Không lưu giá trong sản phẩm. Giá nhập/bán ghi trên từng dòng chứng từ.
- **BR-PRD-08:** Sản phẩm không xóa cứng; ngừng kinh doanh bằng `active = false`.
- **BR-PRD-09:** Không tạo mới sản phẩm trùng tên với sản phẩm **đã ngừng kinh doanh** (tránh một mặt hàng có hai mã, lịch sử bị tách đôi). Hệ thống từ chối kèm mã SP cũ và hướng dẫn **mở bán lại** (`PUT` với `active: true`). _(Xác nhận 2026-10-15)_
- **BR-PRD-10:** Khi thêm hoặc sửa SP (form và file Excel) được chọn **một NCC** cung cấp SP đó — **tùy chọn** _(sửa 2026-10-28: chọn một cho đơn giản; quan hệ NCC–SP vẫn nhiều–nhiều, SP cần thêm NCC thì gán ở màn Nhà cung cấp; API vẫn nhận mảng để sau mở rộng)_. Chọn = tạo hoặc bật lại liên kết BR-SUP-05; đổi NCC ở form sửa = gỡ NCC cũ, gán NCC mới; xóa trắng = gỡ. SP đang có **nhiều** NCC thì form chỉ hiển thị, không sửa NCC (tránh gỡ nhầm). Áp dụng BR-SUP-06 cho liên kết mới; liên kết sẵn có với NCC nay đã ngừng được giữ (BR-SUP-07). Tạo SP và gán NCC trong **cùng một transaction**: NCC lỗi thì SP không được tạo. Excel có **một cột "Nhà cung cấp"** là dropdown "mã · tên" các NCC đang hoạt động; khi đọc chỉ lấy phần mã. _(Xác nhận 2026-10-28)_

## Danh mục sản phẩm

### ✅ Đã xác nhận (2026-10-14)

- **BR-CAT-01:** Danh mục (ví dụ: cám gà, cám heo, cám cá) quản lý bằng bảng riêng, có CRUD.
- **BR-CAT-02:** Tên danh mục duy nhất, không phân biệt hoa thường.
- **BR-CAT-03:** Danh mục không xóa cứng; ngừng dùng bằng `active = false`.
- **BR-CAT-04:** Không tạo mới danh mục trùng tên với danh mục đã ngừng dùng; hệ thống hướng dẫn mở lại. _(Xác nhận 2026-10-15)_

## Đơn vị tính

### ✅ Đã xác nhận (2026-10-28)

- **BR-UNIT-01:** Đơn vị tính (bao, kg, túi, thùng…) là danh mục **do người dùng tự quản lý**: thêm, đổi tên, ngừng dùng, mở lại. Tên duy nhất, không phân biệt hoa thường. Có sẵn **Bao** và **Kg** (chuyển từ `BAG`/`KG` cũ).
- **BR-UNIT-02:** Không xóa cứng; ngừng dùng bằng `active = false`. SP đang dùng đơn vị đã ngừng vẫn giữ nguyên và vẫn sửa được; chỉ chặn **chọn mới** đơn vị đã ngừng. Tạo trùng tên với đơn vị đã ngừng → hướng dẫn mở lại (như BR-CAT-04).
- **BR-UNIT-03:** Đơn vị có **khối lượng cố định** (hiện chỉ Kg = 1 kg) thì khối lượng tịnh của SP phải đúng bằng số đó. Người dùng **không** đặt được khối lượng cố định; đơn vị tự thêm luôn để khối lượng theo từng SP.
- **BR-UNIT-04:** Đổi tên đơn vị thì mọi chứng từ cũ hiển thị **tên mới** (không chụp tên lúc lập phiếu), giống tên sản phẩm.
- Quản lý đơn vị tính và danh mục: chỉ **Quản lý hệ thống**; mọi vai trò được xem. Giao diện: nút **Cấu hình** trên trang Sản phẩm (không thêm mục menu).

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

- **BR-SUP-01:** Mỗi NCC có mã, tên; **mã số thuế**, số điện thoại, email, địa chỉ **tùy chọn**. Email nếu nhập phải đúng định dạng. _(Sửa 2026-10-24: bỏ "người liên hệ (bắt buộc)", thay bằng mã số thuế — dữ liệu người liên hệ cũ bị xóa theo xác nhận, migration V15.)_
- **BR-SUP-02:** Mã NCC do **hệ thống tự sinh** dạng `NCC` + 3 chữ số (ví dụ `NCC001`), duy nhất, **không sửa được**. Mã có thể nhảy số (do rollback) — chấp nhận.
- **BR-SUP-03:** Tên NCC duy nhất, **không phân biệt hoa thường**.
- **BR-SUP-04:** NCC không xóa cứng; ngừng hoạt động bằng `active = false`. Không tạo mới NCC trùng tên với NCC đã ngừng hoạt động; hệ thống hướng dẫn mở lại (giống BR-PRD-09).
- **BR-SUP-05:** Quản lý **NCC nào được cung cấp SP nào** bằng bảng nối `product_supplier` (nhiều–nhiều). Mỗi cặp (NCC, SP) chỉ có một dòng: gỡ liên kết = `active = false`, gán lại = bật `active = true` trên chính dòng đó.
- **BR-SUP-06:** Không gán SP **đã ngừng kinh doanh**, không gán SP cho NCC **đã ngừng hoạt động**.
- **BR-SUP-07:** Ngừng hoạt động NCC **giữ nguyên** các liên kết (mở lại NCC không phải gán lại).
- **BR-SUP-08:** **Chặn cứng** khi nhập kho: phiếu nhập từ NCC X chỉ được chứa SP đang liên kết `active = true` với X. _(Thực thi ở module nhập kho.)_
- **BR-SUP-09:** Mã số thuế (tùy chọn) gồm **10 chữ số**, hoặc 10 chữ số + `-` + 3 chữ số cho chi nhánh (ví dụ `0101234567-001`); khoảng trắng được bỏ trước khi kiểm và lưu. Có nhập thì **duy nhất** giữa các NCC, kể cả NCC đã ngừng (trùng → báo NCC đang giữ, hướng dẫn mở lại). Ô tìm kiếm NCC khớp cả mã số thuế. _(Xác nhận 2026-10-24)_

---

## Khách hàng

### ✅ Đã xác nhận (2026-10-20)

- **BR-CUS-01:** Mỗi khách có mã, tên, loại, **số điện thoại (bắt buộc)**; người liên hệ, địa chỉ, ghi chú **tùy chọn**.
- **BR-CUS-02:** Mã khách do **hệ thống tự sinh** dạng `KH` + 4 chữ số (`KH0001`), duy nhất, **không sửa được**. Có thể nhảy số (rollback) — chấp nhận. Quá 9999 thì mã dài thêm (`KH10000`), vẫn duy nhất — _đúng từ V10; trước đó `lpad` cắt cụt mã, xem [lessons/4-nen-tang-he-thong/03-ma-tu-sinh.md](../../lessons/4-nen-tang-he-thong/03-ma-tu-sinh.md)_.
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
- **BR-EMP-02:** Mỗi nhân viên có **lương ngày công**, **đơn giá tấn bốc xuống**, **đơn giá tấn giao hàng**, hoặc nhiều cách cùng lúc (ít nhất một trong ba), mỗi đơn giá ≥ 0, 2 chữ số lẻ (BR-MON-04). Để trống = không hưởng theo cách đó. _(Sửa 2026-10-26: trước đây chỉ có "đơn giá tấn bốc vác" — nay tách đôi, xem BR-EMP-04.)_
- **BR-EMP-03:** Tên được trùng. SĐT tùy chọn, **không** chặn trùng; có nhập thì chuẩn hóa như BR-CUS-06.
- **BR-EMP-04:** Tấn bốc xuống hàng và tấn đi giao hàng là **hai loại tấn có đơn giá khác nhau**. Mỗi nhân viên có thể có một, cả hai, hoặc không có đơn giá tấn nào (khi đó phải có lương ngày). Tấn của loại không có đơn giá vẫn ghi nhận được nhưng **không tính tiền**. _(Xác nhận 2026-10-26)_
- **BR-ATT-01:** Mỗi nhân viên mỗi ngày một dòng chấm công: trạng thái ngày Cả ngày (1 công) / Nửa ngày (0,5) / Nghỉ (0) + số tấn bốc xuống và số tấn giao hàng (mỗi loại ≥ 0, tối đa 3 chữ số lẻ; không nhập = 0). Quản lý nhập tay. _(Sửa 2026-10-26: tách hai loại tấn, xem BR-ATT-03.)_
- **BR-ATT-02:** Ngày Nghỉ thì **cả hai loại tấn** = 0. Không chấm công ngày tương lai (giờ VN). Không chấm công cho nhân viên đã nghỉ.
- **BR-PAY-01:** Lương tháng = tổng công × lương ngày + tổng tấn bốc xuống × đơn giá bốc xuống + tổng tấn giao hàng × đơn giá giao hàng; **mỗi phần trong ba phần làm tròn `HALF_UP` về đồng riêng**, tổng = tổng ba phần đã làm tròn (không làm tròn lại tổng). Ví dụ 41,235 tấn × 52.500 = 2.164.837,5 → 2.164.838. _(Sửa 2026-10-26: từ hai phần thành ba phần, xem BR-PAY-04.)_
- **BR-PAY-02:** Chưa chốt: bảng lương tính trực tiếp từ chấm công với đơn giá hiện tại. **Chốt lương** lưu bản chụp (công, tấn bốc xuống, tấn giao hàng, **đơn giá lúc chốt**) — đổi đơn giá sau không đổi lương đã chốt.
- **BR-PAY-03:** Tháng đã chốt thì **khóa chấm công** của tháng đó. Sửa = **Hủy chốt** (bắt buộc lý do) → bảng lương cũ giữ lại với trạng thái Đã hủy, tháng mở lại để sửa và chốt lại. Mỗi tháng chỉ một bảng lương còn hiệu lực.

- **BR-ATT-03:** Một ngày có thể ghi **cả hai loại tấn** (bốc xuống và giao hàng). Tấn giao hàng nhập tay trên bảng công như tấn bốc xuống (chưa lấy tự động từ phiếu bán, vì phiếu bán không ghi ai giao). _(Xác nhận 2026-10-26)_
- **BR-PAY-04:** Tiền tấn bốc xuống và tiền tấn giao hàng tính **riêng** theo đơn giá của từng loại. Ví dụ 0,001 tấn × 52.500 = 52,5 → 53 ở cả hai loại: 53 + 53 = 106, **không** phải làm tròn tổng 105. Nhân viên không có đơn giá của loại nào thì tấn loại đó không tính tiền (tiền = 0). Dữ liệu trước 2026-10-26: toàn bộ tấn cũ là **tấn bốc xuống**, các bảng lương đã chốt giữ nguyên số tiền.

## Công nợ nhân viên (trả lương, tạm ứng)

> Thiết kế: migration V18. Hai món nợ ngược chiều: **lương chưa trả** (công ty nợ nhân viên) và **tạm ứng** (nhân viên nợ công ty). Cùng mẫu với công nợ khách / NCC: nhật ký bất biến + số tổng có CHECK ở DB.

### ✅ Đã xác nhận (2026-10-26)

- **BR-EPAY-01:** Chỉ bảng lương **đã chốt** mới có lương để trả. Mỗi **dòng lương** (nhân viên × tháng) là một món nợ: còn nợ = lương chốt − đã trả. Trả **theo từng bảng lương tháng**, không gộp nhiều tháng trong một lần trả. Tháng chưa chốt thì chưa nợ.
- **BR-EPAY-02:** Một lần trả lương = **tiền mặt + chuyển khoản + số trừ vào tạm ứng** (người trả tự chọn trừ bao nhiêu). Tổng không vượt số còn nợ của dòng lương; số trừ không vượt **số dư tạm ứng hiện có**. Tiền tính đến đồng. Ngày trả mặc định hôm nay, chọn tự do. Trừ tạm ứng là một "hình thức" thanh toán nhưng **không có tiền ra khỏi quỹ**.
- **BR-EPAY-03:** Tạm ứng là một khoản chi ra (tiền mặt hoặc chuyển khoản). **Số dư tạm ứng của nhân viên luôn ≥ 0**: kiểm tra + trừ trong cùng transaction, khóa dòng nhân viên `FOR UPDATE`, và `CHECK` ở DB. Nhân viên **không trả lại tiền mặt** (đã xác nhận) — chỉ trừ dần vào lương.
- **BR-EPAY-04:** Nhật ký tạm ứng và trả lương **bất biến**. Ghi nhầm → **hoàn** toàn bộ khoản đó (lý do bắt buộc) bằng một dòng hoàn trỏ về khoản gốc; mỗi khoản hoàn một lần. Hoàn khoản trừ tạm ứng thì số dư tạm ứng cộng lại. **Hoàn khoản ứng** chỉ được khi số dư tạm ứng còn đủ (phần đã trừ vào lương phải hoàn trước).
- **BR-EPAY-05:** Bảng lương đã **trả lương** (dù chỉ một người) thì **không hủy chốt được** — phải hoàn các khoản trả trước. Bảng lương chốt **trước module này** coi như **đã trả đủ**: không hiện ở công nợ, không ghi trả lương được; hủy chốt rồi chốt lại một tháng cũ thì bản mới phát sinh nợ bình thường.
- **BR-EPAY-06:** Ai được ứng tiền, trả lương, xem công nợ nhân viên: **chỉ Quản lý hệ thống** (như bảng lương và nhân viên hiện tại). Công nợ nhân viên liệt kê người còn lương chưa trả **hoặc** còn dư tạm ứng; `netAmount = lương chưa trả − dư tạm ứng` (dương: công ty nợ nhân viên).

### ⚠️ Giả định tạm thời

- **BR-EPAY-A01:** Chỉ ứng tiền cho nhân viên **đang làm việc** (giống chấm công, BR-ATT-02). Trả lương cho nhân viên đã nghỉ vẫn được; hoàn khoản ứng cũ của người đã nghỉ vẫn được.
- **BR-EPAY-A02:** Tạm ứng gắn với nhân viên, không gắn với tháng nào; người trả lương tự quyết định trừ vào tháng nào.

## Sổ quỹ (tiền mặt, tài khoản)

> Thiết kế: migration V19. Sổ quỹ đọc thẳng từ các bảng thanh toán đã có qua VIEW `cash_flow_entry` — không chép tiền sang bảng mới.

### ✅ Đã xác nhận (2026-10-26)

- **BR-CASH-01:** Hai nơi giữ tiền: **một quỹ tiền mặt** (`CASH`) và **một tài khoản ngân hàng** (`BANK`). Thu/chi bằng tiền mặt vào quỹ, bằng chuyển khoản vào tài khoản. Không có nhiều tài khoản / nhiều quỹ.
- **BR-CASH-02:** Sổ quỹ gồm: thu tiền khách (+), trả NCC (−), trả lương (−) **trừ phần "trừ tạm ứng"** (không có tiền ra), tạm ứng nhân viên (−), phiếu thu (+) / phiếu chi (−) khác. Dòng hoàn tính ngược chiều khoản gốc. Ngày ghi sổ = ngày thu / trả / ứng / lập phiếu (không phải lúc bấm nút). Một ngày sắp theo thời điểm ghi.
- **BR-CASH-03:** Sổ quỹ bắt đầu từ **ngày đầu kỳ** với số dư tiền mặt và tài khoản (≥ 0, đồng nguyên) do người dùng nhập; khoản có ngày trước đó **không tính**. Ngày đầu kỳ không ở tương lai. Đầu kỳ **sửa được** (là cấu hình), nhưng mọi lần đặt/sửa ghi lịch sử (giá trị cũ, giá trị mới, người sửa, thời điểm). Chưa đặt đầu kỳ thì chưa xem sổ, chưa lập phiếu được. _(2026-10-27: đặt / sửa đầu kỳ chuyển sang menu **Số dư đầu kỳ → Quỹ đầu kỳ**, cùng chỗ với tồn kho đầu kỳ; trang Sổ quỹ chỉ hiện đầu kỳ để đọc)_
- **BR-CASH-04:** **Phiếu thu / phiếu chi khác** (tiền điện, xăng xe, góp vốn...): mã tự sinh `PT` / `PC` + 6 chữ số, có tài khoản (CASH/BANK), danh mục, số tiền (> 0, đồng nguyên), ngày (mặc định hôm nay, chọn tự do nhưng **không trước ngày đầu kỳ**), người nộp / nhận (tùy chọn), ghi chú. Phiếu **bất biến**: ghi nhầm → **phiếu hoàn** (mã gốc + `-H`, lý do bắt buộc, ngày = hôm nay), mỗi phiếu hoàn một lần.
- **BR-CASH-05:** Danh mục thu/chi do người dùng tạo, mỗi danh mục thuộc chiều **Thu** hoặc **Chi** (cố định từ lúc tạo), tên không trùng (không phân biệt hoa thường). Phiếu chỉ dùng danh mục **đang dùng** và **cùng chiều**. Không xóa danh mục, chỉ ngừng dùng.
- **BR-CASH-06:** Số dư được phép **âm** — **không chặn** khoản chi nào, chỉ **cảnh báo đỏ** (số âm báo hiệu số dư đầu kỳ sai hoặc có khoản thu chưa ghi). Số dư "hiện tại" = đầu kỳ + mọi khoản có ngày từ đầu kỳ tới **hôm nay**; khoản ghi ngày tương lai chưa tính.
- **BR-CASH-07:** Xem sổ quỹ, lập phiếu thu/chi, đặt đầu kỳ, quản lý danh mục: **chỉ Quản lý hệ thống** (sổ quỹ lộ cả doanh thu lẫn lương).

### ❓ Câu hỏi mở

- **BR-CASH-Q01:** Có cần **chuyển tiền nội bộ** (nộp tiền mặt vào tài khoản, rút tài khoản về quỹ) không? Hiện có thể ghi tạm bằng một phiếu chi tiền mặt + một phiếu thu tài khoản.
- **BR-CASH-Q02:** Khi nào cần **chặn quỹ âm** hoặc **nhiều tài khoản**? Khi đó phải chuyển sổ quỹ từ VIEW sang bảng ghi kèm từng giao dịch (để khóa số dư) — xem database.md.

---

## Số dư đầu kỳ — Tồn kho

> Thiết kế: migration V20. Phiếu tồn đầu kỳ là một **loại chứng từ** (`OPENING_STOCK`) trong `stock_document`, đi qua đúng cơ chế tồn kho chung (`inventory` + `stock_transaction`). Khác đầu kỳ sổ quỹ (BR-CASH-03, là cấu hình sửa được): tồn đầu kỳ làm tồn tăng thật nên là **chứng từ bất biến**.

### ✅ Đã xác nhận (2026-10-27)

- **BR-OPN-01:** Phiếu tồn đầu kỳ ghi **một kho**, ngày tồn đầu kỳ (mặc định hôm nay, lùi được, **không ở tương lai**), ghi chú và nhiều dòng hàng. Mã tự sinh `DTK` + 6 chữ số. Nhiều kho thì lập nhiều phiếu. Ngày **không ràng buộc** với ngày đầu kỳ sổ quỹ.
- **BR-OPN-02:** Mỗi dòng: SP, số lượng **nguyên > 0**; mỗi SP một dòng; tối đa 1.000 dòng.
- **BR-OPN-03:** **Nháp → Hoàn tất (xác nhận)**, Nháp → Đã hủy, Hoàn tất → Đã đảo — như phiếu nhập (BR-IMP-11..14). Nháp sửa/hủy tự do, **không ảnh hưởng tồn**. Xác nhận: cộng tồn và ghi `StockTransaction` IN cho mọi dòng **trong một transaction**; một dòng lỗi → rollback hết, phiếu vẫn là Nháp. Phiếu đã xác nhận bị khóa. Ghi **người lập** và **người xác nhận** (tên đăng nhập).
- **BR-OPN-04:** Kho và SP phải **đang hoạt động**, kiểm tra cả lúc lưu nháp lẫn lúc xác nhận.
- **BR-OPN-05:** Mỗi **cặp (SP, kho)** chỉ được khai đầu kỳ khi **chưa có biến động tồn nào** (chưa từng nhập, bán, đầu kỳ — kể cả đã nhập rồi bán hết, tồn về 0). Biến động của phiếu đầu kỳ **đã đảo** không tính. Hệ quả: kho mới → lập phiếu cho kho đó; quên một SP chưa phát sinh → lập phiếu bổ sung; SP đã nhập/bán → **từ chối**, lệch số thì dùng kiểm kê. Lưu nháp vẫn được (giao diện cảnh báo, làm mờ SP), chặn lúc xác nhận và liệt kê mọi SP vi phạm.
- **BR-OPN-06:** Khai sai → **đảo toàn phiếu** (lý do bắt buộc), mỗi phiếu đảo một lần; tồn hiện tại không đủ để trừ lại (đã bán bớt) → từ chối. Đảo xong, các SP của phiếu được khai đầu kỳ lại.
- **BR-OPN-07:** **Không có "đầu kỳ năm mới".** Tồn kho chạy liên tục từ lần khởi tạo; sang năm không khởi tạo lại. Khởi tạo (đầu kỳ), chuyển kỳ (không cần) và kiểm kê (module sau) là ba việc khác nhau.
- **BR-OPN-08:** Phiếu tồn đầu kỳ **không có NCC, không có khách, không phát sinh công nợ, không vào sổ quỹ**, không tính vào hàng "Đang về". CHECK ở DB chặn.
- **BR-OPN-09:** Lập, xác nhận, hủy, đảo, xem phiếu tồn đầu kỳ: **chỉ Quản lý hệ thống** (tạm thời — sẽ chia lại vai trò sau). Trang Lịch sử kho vẫn hiện giao dịch đầu kỳ cho mọi vai trò, nhưng chỉ Quản lý hệ thống mở được phiếu.
- **BR-OPN-10:** Đơn giá **tùy chọn** (không nhập = 0), chỉ để tham khảo giá trị tồn đầu kỳ — hệ thống chưa tính giá vốn (BR-IMP-09).

### ❓ Câu hỏi mở

- **BR-OPN-Q01:** Nhập dòng tồn đầu kỳ từ Excel (BR-XLS-Q01) — để sau, khi màn hình nhập tay đã dùng ổn. Thiết kế dự kiến: dùng lại ADR-0004, import thay toàn bộ dòng của một phiếu **nháp**.
- ~~BR-OPN-Q02: Kiểm kê / điều chỉnh tồn cho kho đã vận hành~~ — đã làm, xem mục **Kiểm kê kho** (BR-CNT).
- ~~BR-OPN-Q03: Công nợ đầu kỳ khách và NCC~~ — đã làm, xem mục **Số dư đầu kỳ — Công nợ khách / NCC** (BR-ODB).

---

## Số dư đầu kỳ — Công nợ khách / NCC

> Thiết kế: migration V21. Mỗi khoản nợ cũ là một **chứng từ không có kho, không có dòng hàng** trong `stock_document` (`OPENING_CUSTOMER_DEBT` / `OPENING_SUPPLIER_DEBT`), để thu / trả, hoàn khoản thu nhầm, trang công nợ và sổ quỹ dùng lại nguyên cơ chế của phiếu bán / phiếu nhập.

### ✅ Đã xác nhận (2026-10-27)

- **BR-ODB-01:** Nợ đầu kỳ của **khách** (khách nợ mình, mã `DKH` + 6 số) và của **NCC** (mình nợ NCC, mã `DNC` + 6 số). Mỗi khoản: một khách / NCC, số nợ, ngày chốt nợ, ghi chú, người ghi nhận. **Không có** khoản mình nợ ngược khách (khách trả trước) — xem BR-SAL-Q05.
- **BR-ODB-02:** Mỗi khách / NCC nhập **một tổng số** (chưa chi tiết từng hóa đơn cũ — để sau nếu cần). Số nợ **> 0**, đồng nguyên. Ngày chốt nợ mặc định hôm nay, lùi được, không ở tương lai; **không ràng buộc** với ngày đầu kỳ sổ quỹ.
- **BR-ODB-03:** Mỗi khách / NCC chỉ **một khoản nợ đầu kỳ còn hiệu lực**. Khoản đã đảo không tính → nhập lại được. Khách đã có phiếu bán trong hệ thống vẫn nhập được nợ cũ (hai khoản độc lập). UNIQUE ở DB chặn cả khi hai người ghi cùng lúc.
- **BR-ODB-04:** Ghi nhận **theo lô** trên một bảng nhiều dòng, chung một ngày chốt nợ; mỗi dòng thành một khoản, **tất cả hoặc không gì** (một dòng lỗi → không ghi dòng nào). Không có nháp — mỗi khoản chỉ là một con số. Khách / NCC phải đang hoạt động; trùng khách trong lô → báo lỗi.
- **BR-ODB-05:** Trang **Công nợ khách / Công nợ NCC** cộng cả nợ đầu kỳ: tổng còn nợ, số khoản, "nợ từ ngày"; mở rộng một khách thấy dòng nợ đầu kỳ phía trên các phiếu.
- **BR-ODB-06:** Ghi sai → **đảo** khoản đó (lý do bắt buộc), chỉ khi **chưa thu / trả đồng nào** (như BR-SAL-14, BR-SPAY-04); đảo xong nhập lại số đúng. Không sửa.
- **BR-ODB-07:** Thu (khách) / trả (NCC) nợ cũ **y như phiếu bán / phiếu nhập**: tiền mặt và/hoặc chuyển khoản, không vượt số còn nợ, ngày chọn tự do, lịch sử bất biến, hoàn khoản ghi nhầm (BR-SAL-11..13, BR-SPAY-02..03). Tiền thu / trả nợ cũ **vào sổ quỹ**; bản thân việc ghi nhận nợ đầu kỳ **không** làm sổ quỹ thay đổi.
- **BR-ODB-08:** Ghi nhận và đảo nợ đầu kỳ: **chỉ Quản lý hệ thống**. Xem và thu / trả: theo quyền công nợ tương ứng — nợ khách: Nhân viên bán hàng + Quản lý hệ thống; nợ NCC: Quản lý kho + Quản lý hệ thống (thu nợ cũ là việc hằng ngày như thu nợ phiếu bán).
- **BR-ODB-09:** Menu **Số dư đầu kỳ** gom: Quỹ đầu kỳ, Tồn kho đầu kỳ, Công nợ khách đầu kỳ, Công nợ NCC đầu kỳ.

---

## Kiểm kê kho

> Thiết kế: migration V22. Phiếu kiểm kê là loại chứng từ `STOCK_COUNT` trong `stock_document`; mỗi dòng giữ SL sổ sách đã chụp và SL đếm. Xác nhận ghi **phần chênh** qua cơ chế tồn kho chung — không "đặt tồn = số đếm".

### ✅ Đã xác nhận (2026-10-27)

- **BR-CNT-01:** Phiếu kiểm kê ghi **một kho đang hoạt động**, ngày kiểm (mặc định hôm nay, lùi được, không ở tương lai), ghi chú. Mã `KK` + 6 số. Ghi người lập, người xác nhận.
- **BR-CNT-02:** Lập phiếu thì hệ thống **nạp sẵn mọi SP đang có tồn > 0** tại kho. Được bỏ dòng (không kiểm SP đó) và thêm SP chưa có trong danh sách (kể cả SP sổ sách đang 0) — tức kiểm toàn kho hoặc từng phần. Mỗi SP một dòng.
- **BR-CNT-03:** SL sổ sách được **chụp lúc lập phiếu** (SP thêm sau: chụp lúc thêm) và giữ nguyên khi lưu. **Không khóa kho** trong lúc kiểm — nhập/bán vẫn chạy. SP có nhập/xuất **sau lúc chụp** được cảnh báo; nút **Lấy lại số sổ sách** chụp lại mọi dòng theo tồn hiện tại (giữ SL đếm đã nhập).
- **BR-CNT-04:** SL đếm là số nguyên ≥ 0; để trống = chưa đếm (lưu nháp được). Xác nhận đòi **mọi dòng đã đếm**.
- **BR-CNT-05:** Dòng lệch (đếm ≠ sổ sách) bắt buộc chọn **lý do đang dùng** lúc xác nhận, kèm ghi chú tùy chọn. Danh mục lý do **do người dùng tự quản lý** (thêm, đổi tên, ngừng dùng; tên không trùng; không xóa). Có sẵn 4 lý do mặc định.
- **BR-CNT-06:** Xác nhận ghi **phần chênh** trong **một transaction**: thừa → tăng tồn (IN), thiếu → giảm tồn (OUT), khớp → không ghi gì. Ví dụ sổ sách 120, đếm 115, sau lúc chụp bán 10 → xác nhận ghi −5, tồn 110 → 105 (không phải 115). Dòng thiếu mà tồn hiện tại không đủ trừ → từ chối, rollback hết, phiếu vẫn đang kiểm.
- **BR-CNT-07:** Mỗi kho chỉ **một phiếu đang kiểm** — xác nhận hoặc hủy phiếu đó trước khi lập phiếu mới. UNIQUE ở DB chặn cả khi hai người lập cùng lúc.
- **BR-CNT-08:** Sai thì **đảo toàn phiếu** (lý do bắt buộc), mỗi phiếu một lần; mọi điều chỉnh ghi ngược lại; không đủ tồn để trừ phần thừa → từ chối. Phiếu đang kiểm thì hủy được (chưa tác động tồn).
- **BR-CNT-09:** Danh sách phiếu hiện: số dòng đã đếm / tổng, số dòng lệch, tổng thừa, tổng thiếu. Lịch sử kho hiện "Kiểm kê thừa / thiếu / Đảo kiểm kê". **Không tính tiền chênh lệch** (chưa có giá vốn).
- **BR-CNT-10:** Kiểm kê và danh mục lý do: **chỉ Quản lý hệ thống** (tạm thời — sẽ chia lại vai trò sau).

### ❓ Câu hỏi mở

- **BR-CNT-Q01:** In biên bản kiểm kê / xuất Excel phiếu đếm để đếm trên giấy rồi nhập lại — để sau.

## Tài khoản, đăng nhập, phân quyền

> Thiết kế kỹ thuật: [ADR-0003](../decisions/ADR-0003-authentication.md).

### ✅ Đã xác nhận (2026-10-20)

- **BR-AUTH-01:** Tên đăng nhập 3–50 ký tự (chữ không dấu, số, `.` `_` `-`), lưu chữ thường, duy nhất, không đổi được sau khi tạo. Không xóa tài khoản: khóa bằng `active = false`.
- **BR-AUTH-02:** Ba vai trò: Quản lý kho, Nhân viên bán hàng, Quản lý hệ thống. Một người có **một hoặc nhiều** vai trò; quyền = hợp quyền các vai trò (ma trận ở ADR-0003 mục 5, kiểm ở backend).
- **BR-AUTH-03:** Mật khẩu tối thiểu 8 ký tự, tối đa 72 byte (giới hạn BCrypt); chỉ lưu dạng băm BCrypt.
- **BR-AUTH-04:** Sai tên hoặc sai mật khẩu → cùng một thông báo. Tài khoản bị khóa chỉ được báo "bị khóa" khi mật khẩu đúng.
- **BR-AUTH-05:** Luôn còn ít nhất một Quản lý hệ thống đang hoạt động. Không tự khóa tài khoản đang đăng nhập.
- **BR-AUTH-06:** Phiên hết hạn sau 8 giờ không thao tác, tối đa 24 giờ từ lúc đăng nhập. Đăng nhập nhiều thiết bị được phép; đăng xuất chỉ ảnh hưởng thiết bị đó. Đổi vai trò / khóa tài khoản → mọi phiên của người đó bị thu hồi, quyền cũ còn tối đa 15 phút.
- **BR-AUTH-07:** Tự đổi mật khẩu (cần mật khẩu cũ) → các thiết bị khác phải đăng nhập lại. Quản lý hệ thống đặt lại mật khẩu → người đó đăng nhập lại trên mọi thiết bị.
- **BR-AUTH-08:** Tài khoản Quản lý hệ thống đầu tiên được tạo khi khởi động nếu chưa có tài khoản nào, từ `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` trong `.env`.

---

## App khách hàng (tự đặt hàng)

### ✅ Đã xác nhận (2026-11-03, ADR-0005, migration V27)

- **BR-POR-01:** Khách đăng nhập app bằng **SĐT của mình** + mật khẩu. Tài khoản app tách hẳn khỏi tài khoản nhân viên: token khách không dùng được ở bất kỳ API nào của cửa hàng và ngược lại.
- **BR-POR-02:** **Cửa hàng tạo tài khoản** cho khách (bán hàng hoặc quản lý hệ thống), đặt mật khẩu ban đầu và báo cho khách. Mỗi khách tối đa một tài khoản. Khách đang ngừng hoạt động không tạo tài khoản và không đăng nhập được.
- **BR-POR-03:** Mật khẩu do cửa hàng đặt (tạo mới / đặt lại) → khách **phải đổi** trước khi đặt, sửa, hủy đơn. Mật khẩu theo BR-AUTH-03.
- **BR-POR-04:** Sai mật khẩu **5 lần liên tiếp → khóa tạm 15 phút**; đăng nhập đúng thì đếm lại từ 0. Sai SĐT hay sai mật khẩu cùng một thông báo.
- **BR-POR-05:** Phiên app hết hạn sau **30 ngày không dùng**, tối đa **90 ngày** từ lúc đăng nhập (khác nhân viên — BR-AUTH-06). Đăng xuất / đổi mật khẩu như BR-AUTH-06, 07.
- **BR-POR-06:** Khách chỉ thấy **SP đang kinh doanh** (mã, tên, ĐVT, danh mục) — **không thấy giá, không thấy tồn**.
- **BR-POR-07:** Khách đặt hàng = **đơn Chờ xác nhận kênh App** (BR-SAL-16..22): SP + SL theo ĐVT, ngày muốn giao, ghi chú. Cửa hàng xác nhận / hủy như đơn ghi từ điện thoại.
- **BR-POR-08:** Khách chỉ thấy **đơn của chính mình**. Trạng thái hiển thị: Chờ cửa hàng xác nhận · Đã nhận đơn · Đã giao · Đã hủy · Đã hủy giao. **Giá chỉ hiện sau khi cửa hàng xác nhận** (dòng chốt: SL, đơn giá, thành tiền, tổng; luôn là giá mới nhất kể cả sau BR-SAL-23). Không hiện công nợ, kho xuất, người lập / xác nhận. Đơn bị hủy hiện lý do theo BR-POR-11.
- **BR-POR-09:** Khách **sửa / tự hủy** đơn khi còn **Chờ xác nhận** (BR-SAL-18). Tự hủy ghi lý do "Khách hủy qua app" (+ ghi chú của khách nếu có). Đã nhận đơn thì phải gọi cửa hàng.
- **BR-POR-10:** Cửa hàng **khóa** tài khoản (khách không đăng nhập, không gia hạn, không đặt hàng được ngay — đơn đã có giữ nguyên), **mở khóa** (xóa đếm sai mật khẩu), **đặt lại mật khẩu** (mọi phiên của khách bị thu hồi, BR-POR-03 áp dụng lại). Khách đang mở app khi bị khóa → app tự đăng xuất và hiện lý do.
- **BR-POR-11 (chốt 2026-11-04):** Thông tin nội bộ không ra app khách.
  - **Ghi chú tách đôi:** lời khách ghi trên app lưu riêng (`customer_note`), nhân viên chỉ đọc ("Khách ghi"). Ô "Ghi chú nội bộ" của cửa hàng khách **không bao giờ** thấy; khách sửa đơn cũng không xóa ghi chú nội bộ.
  - **Lý do hủy:** khách tự hủy → hiện lý do của khách. Cửa hàng hủy / hủy giao → khách chỉ thấy câu chung "Cửa hàng đã hủy (giao) đơn này. Vui lòng liên hệ cửa hàng nếu cần".
  - Đơn app luôn giữ kênh **App**: nhân viên sửa phần khách đặt không đổi sang kênh khác được.
- **BR-POR-12 (chốt 2026-11-04):** Giới hạn đơn khách tự đặt qua app (nhân viên ghi đơn hộ không bị giới hạn) — chặn một tài khoản làm ngập danh sách Chờ xác nhận:
  - Tối đa **5 đơn app đang Chờ xác nhận** mỗi khách (đơn thứ 6 → 409; hủy / được xác nhận bớt thì đặt tiếp).
  - Tối đa **50 sản phẩm** một đơn; mỗi sản phẩm **1–9.999**, số nguyên.
  - Ngày muốn giao: **từ hôm nay đến 60 ngày tới** (giờ VN), hoặc để trống.
  - Đăng nhập app: tối đa khoảng **10 lần / phút mỗi IP** (Nginx, dồn được 10) — chống thử một mật khẩu cho nhiều SĐT.

### ❓ Câu hỏi mở

- **BR-POR-Q01:** Khách tự đăng ký + OTP, quên mật khẩu tự phục vụ — khi nào cần? (Hiện: gọi cửa hàng đặt lại.)
- **BR-POR-Q02:** Thông báo cho khách khi đơn được xác nhận / giao (cần service worker hoặc Zalo ZNS).

---

## Báo cáo tồn kho & tổng quan

### ✅ Đã xác nhận (2026-10-29) — mô phỏng báo cáo của KiotViet / MISA

- **BR-RPT-01:** **Tổng quan** là trang đầu sau đăng nhập. Thẻ tồn kho (mọi vai trò): tổng tấn đang tồn (mọi SP, mọi kho, kể cả đã ngừng — BR-STK-01), số SP **đang kinh doanh** hết hàng / sắp hết (theo BR-STK-04, bấm vào mở danh sách SP đã lọc), hàng đang về (tấn, BR-STK-06), số phiếu nhập nháp.
- **BR-RPT-02:** **Doanh số** = tổng tiền phiếu bán **Hoàn tất** theo **ngày bán**; phiếu **đã đảo không tính**. Thẻ bán hàng (mọi vai trò): hôm nay, tháng này (doanh số, số phiếu, số tấn), số phiếu bán nháp, số đơn khách đặt **chờ xác nhận** (BR-SAL-22).
- **BR-RPT-03:** Biểu đồ doanh số **30 ngày gần nhất** (kể cả hôm nay, ngày không bán = 0). Thẻ tiền chỉ hiện với vai trò có quyền: khách còn nợ (quyền công nợ khách), còn nợ NCC (quyền nhập kho), tiền mặt / tài khoản (quyền sổ quỹ).
- **BR-RPT-04:** **Tồn theo kho**: bảng SP × kho (cột mỗi kho đang hoạt động hoặc đã ngừng mà còn hàng), tổng SL + tấn mỗi SP, dòng tổng **chỉ tính tấn** (cộng bao với kg là vô nghĩa). Lọc danh mục, từ khóa (không dấu); mặc định ẩn SP hết hàng; SP đã ngừng kinh doanh chỉ hiện khi còn hàng. Không phân trang.
- **BR-RPT-05:** **Nhập – xuất – tồn theo kỳ** (chọn nhanh hôm nay / hôm qua / 7 ngày / tháng này / tháng trước, hoặc tự chọn; lọc kho, danh mục): mỗi SP có tồn đầu, nhập, xuất, tồn cuối (SL + tấn), **theo ngày ghi sổ** (thời điểm hoàn tất / đảo phiếu, giờ Việt Nam). Giao dịch **đảo** bù trừ vào cột của giao dịch gốc (đảo phiếu nhập làm giảm cột Nhập, không cộng vào Xuất). Tồn cuối = tồn đầu + nhập − xuất; kỳ kết thúc hôm nay thì tồn cuối = tồn hiện tại. Không có cột giá trị tiền (hệ thống chưa tính giá vốn).
- **BR-RPT-06:** Hai báo cáo **in A4 ngang** qua trình duyệt (đầu trang tên cửa hàng, điều kiện lọc, dòng tổng). Mọi vai trò xem được báo cáo tồn.

---

## In phiếu

### ✅ Đã xác nhận (2026-10-28)

- **BR-PRT-01:** **Thông tin cửa hàng** (tên — bắt buộc; địa chỉ, SĐT, lời chào cuối phiếu — tùy chọn) in ở đầu / cuối phiếu. Một bản duy nhất, Quản lý hệ thống sửa ở menu tài khoản → "Thông tin cửa hàng"; mọi vai trò đọc được để in. Chưa cấu hình thì phiếu vẫn in, chỉ thiếu phần đầu.
- **BR-PRT-02:** **Phiếu bán hàng** — **một mẫu dùng chung** đưa khách và lái xe / bốc vác _(sửa 2026-10-28)_, khổ **K80** kiểu bill siêu thị: số phiếu, ngày bán, khách + SĐT, từng dòng "SL ĐVT x đơn giá → thành tiền", tổng cộng, **số tiền bằng chữ**. Phiếu đã hoàn tất in thêm đã thanh toán và còn nợ **của phiếu đó** (không in tổng nợ của khách). Phiếu **nháp** in được, có dòng "PHIẾU TẠM TÍNH – CHƯA HOÀN TẤT".
- **BR-PRT-03:** In được khi **nháp** (lái xe cầm phiếu để xếp hàng; thiếu hàng thì sửa phiếu nháp rồi in lại). _(2026-10-28: bỏ mẫu "phiếu xuất kho" riêng không giá — chủ cửa hàng chọn dùng chung phiếu bán. API vẫn trả `tons` / `totalTons` của phiếu bán, chưa dùng khi in.)_
- **BR-PRT-04:** In bản **đã lưu** của phiếu: đang sửa nháp chưa lưu thì không cho in. Phiếu đã hủy / đã đảo không in. In qua hộp thoại in của trình duyệt (chọn máy in nhiệt K80); chưa ghi "người lập" vì chứng từ chưa lưu người tạo.

---

## Nhập hàng loạt từ Excel (danh mục)

> Áp dụng cho: sản phẩm, khách hàng, nhà cung cấp, nhân viên. Thiết kế kỹ thuật: [ADR-0004](../decisions/ADR-0004-excel-import.md).

### ✅ Đã xác nhận (2026-10-24)

- **BR-XLS-01:** Người dùng tải **file mẫu .xlsx do hệ thống sinh ra** để mọi người điền cùng một định dạng. Sheet "Dữ liệu" có dòng tiêu đề (cột bắt buộc có dấu `*`), cột chọn cố định có dropdown (danh mục đang dùng, đơn vị, loại khách). Sheet "Hướng dẫn" giải thích từng cột kèm một dòng ví dụ. Mẫu **không có cột mã** vì mã do hệ thống tự sinh.
- **BR-XLS-02:** Nhập gồm hai bước. **Kiểm tra**: không ghi gì, trả về mọi lỗi. **Xác nhận**: hệ thống kiểm tra lại từ đầu và chỉ ghi khi cả file không còn lỗi, **tất cả trong một transaction**. Có một dòng lỗi thì **không dòng nào được ghi**.
- **BR-XLS-03:** Mỗi dòng phải qua đúng các quy tắc của form nhập tay (BR-PRD, BR-CUS, BR-SUP, BR-EMP). Lỗi được báo theo **số dòng trong Excel** (dòng tiêu đề là 1) và **tên cột**, và hệ thống gom hết lỗi của cả file trong một lần kiểm tra.
- **BR-XLS-04:** Chỉ nhận `.xlsx`. Tối đa **1.000 dòng** dữ liệu, **2 MB** mỗi file. Dòng trống hoàn toàn được bỏ qua.
- **BR-XLS-05:** File phải là mẫu đúng loại và đúng phiên bản (mã mẫu nằm trong sheet ẩn), tiêu đề cột không bị đổi tên hay đổi thứ tự. Nếu sai, hệ thống báo lỗi cả file và yêu cầu tải lại mẫu.
- **BR-XLS-06:** Quyền nhập giống quyền tạo tay của từng loại. Tải mẫu theo quyền xem.
- **BR-XLS-07:** Dòng trùng bản ghi đã có (tên SP, SĐT khách, tên hoặc MST NCC, **kể cả bản ghi đã ngừng**) thì **báo lỗi**, không cập nhật bản ghi cũ. Hai dòng trùng nhau trong cùng một file cũng báo lỗi ("Trùng với dòng N").

### ⚠️ Giả định tạm thời

- **BR-XLS-A01:** Nhân viên không có khóa duy nhất (BR-EMP-03), nên import **không so trùng với DB**, giống form nhập tay. Trong cùng một file, chỉ chặn hai dòng **cùng tên và cùng SĐT** (thường do dán lặp). Hệ quả: nhập lại cùng một file hai lần sẽ tạo nhân viên trùng.
- **BR-XLS-A02:** Sản phẩm có đơn vị khối lượng cố định (Kg) mà để trống khối lượng tịnh thì được hiểu là khối lượng đó — 1 kg (BR-UNIT-03), giống form nhập tay tự điền. Cột Đơn vị ghi theo **tên** đơn vị đang dùng.

### ❓ Câu hỏi mở

- **BR-XLS-Q01:** Có cần nhập **tồn đầu kỳ** từ Excel không? Nếu có, phải tạo chứng từ điều chỉnh "tồn đầu kỳ" và ghi `StockTransaction` (BR-INV-04), không được ghi thẳng vào bảng tồn. _(2026-10-27: chứng từ tồn đầu kỳ đã có — BR-OPN; phần Excel để sau, xem BR-OPN-Q01)_
- **BR-XLS-Q02:** Có cần nhập **công nợ đầu kỳ** của khách không? _(2026-10-27: công nợ đầu kỳ khách / NCC đã có — BR-ODB, nhập theo lô trên web; nhập từ Excel chưa làm)_
- **BR-XLS-Q03:** Có cần nhập liên kết NCC–SP từ Excel không? Hiện liên kết được gán trên màn hình NCC.
