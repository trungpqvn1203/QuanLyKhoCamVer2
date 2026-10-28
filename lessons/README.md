# Kho bài học — Grain Warehouse

Kiến thức kỹ thuật tích lũy qua từng module, mỗi bài gồm **khái niệm → ví dụ trong dự án kho cám → vì sao quan trọng**,
lỗi đã gặp và câu hỏi ôn tập. Do AI ghi sau mỗi module (CLAUDE.md, cách làm việc mục 4, 8, 9); `NOTES.md` vẫn là sổ tay riêng của bạn.

## Thứ tự đọc gợi ý

1. **Nền tảng hệ thống** — cách mọi màn hình lọc, phân quyền, sinh mã.
2. **Kho hàng** — trái tim nghiệp vụ: chứng từ, tồn kho không âm, lịch sử bất biến, khóa dòng.
3. **Tiền và công nợ** — cùng khuôn mẫu "chứng từ + thanh toán bất biến + hoàn", áp vào khách, NCC, nhân viên, sổ quỹ.
4. **Danh mục** — các module CRUD và nhập Excel.
5. **Vận hành** — đóng gói và đưa lên server.

## Mục lục

### Kho hàng: chứng từ, tồn kho, kiểm kê

| Bài | Nội dung chính |
|---|---|
| [Nhập kho](1-kho-hang/01-nhap-kho.md) | Chứng từ, trạng thái, khóa FOR UPDATE, giao dịch kho bất biến |
| [Tồn kho trên trang Sản phẩm](1-kho-hang/02-ton-kho-tren-san-pham.md) | Tổng tồn, tình trạng tồn, hàng đang về |
| [Bán hàng, bảng giá theo khách, công nợ khách](1-kho-hang/03-ban-hang.md) | Trừ tồn có điều kiện, thanh toán bất biến |
| [Lịch sử biến động tồn kho](1-kho-hang/04-lich-su-ton-kho.md) | Đọc lại stock_transaction để dò tồn từng bước |
| [Tồn kho đầu kỳ](1-kho-hang/05-ton-dau-ky.md) | Khởi tạo tồn bằng chứng từ, chặn trùng hai lớp |
| [Kiểm kê kho](1-kho-hang/06-kiem-ke.md) | Chụp sổ sách, ghi phần chênh, không khóa kho |

### Tiền và công nợ

| Bài | Nội dung chính |
|---|---|
| [Công nợ nhà cung cấp](2-tien-va-cong-no/01-cong-no-ncc.md) | Trả tiền theo phiếu nhập, cờ dữ liệu cũ |
| [Công nợ nhân viên: lương theo tấn, trả lương, tạm ứng](2-tien-va-cong-no/02-cong-no-nhan-vien.md) | Tách tấn bốc/giao, trừ tạm ứng, khóa theo thứ tự |
| [Sổ quỹ — tiền mặt và tài khoản](2-tien-va-cong-no/03-so-quy.md) | VIEW thay vì chép dữ liệu, số dư lũy kế |
| [Công nợ đầu kỳ khách / NCC](2-tien-va-cong-no/04-cong-no-dau-ky.md) | Dùng lại cơ chế thanh toán, partial unique index |

### Danh mục: nhà cung cấp, khách hàng, nhân viên, cấu hình sản phẩm

| Bài | Nội dung chính |
|---|---|
| [Nhà cung cấp](3-danh-muc/01-nha-cung-cap.md) | Liên kết NCC–SP, mã số thuế, chọn NCC từ form SP (đảo chiều phụ thuộc) |
| [Khách hàng](3-danh-muc/02-khach-hang.md) | SĐT duy nhất, loại khách |
| [Nhân viên, chấm công, bảng lương](3-danh-muc/03-nhan-vien-cham-cong-luong.md) | Chốt lương, khóa tháng |
| [Nhập hàng loạt từ Excel](3-danh-muc/04-nhap-excel.md) | Hai bước kiểm tra / xác nhận, tất cả hoặc không gì |
| [Cấu hình sản phẩm: danh mục và đơn vị tính](3-danh-muc/05-cau-hinh-san-pham.md) | Enum/CHECK → bảng danh mục, migration chuyển dữ liệu, EAGER vs LAZY |

### Nền tảng hệ thống

| Bài | Nội dung chính |
|---|---|
| [Đăng nhập & phân quyền](4-nen-tang-he-thong/01-dang-nhap-phan-quyen.md) | JWT trong cookie, CSRF, ma trận quyền |
| [Bộ lọc dùng chung và giao diện danh sách](4-nen-tang-he-thong/02-bo-loc-dung-chung-va-ui.md) | SearchCriteria + Specification, chuẩn UI |
| [Mã tự sinh](4-nen-tang-he-thong/03-ma-tu-sinh.md) | Sequence, lỗi mã bị cắt cụt |
| [In phiếu (K80)](4-nen-tang-he-thong/04-in-phieu.md) | Phiếu bán K80, in qua iframe, số tiền bằng chữ, `tsc -b` |

### Vận hành: đóng gói và triển khai

| Bài | Nội dung chính |
|---|---|
| [Bản chất deploy](5-van-hanh/01-ban-chat-deploy.md) | Code / cấu hình / dữ liệu, cập nhật và rollback |
| [Đóng gói & triển khai: Docker Compose + Nginx](5-van-hanh/02-docker-compose-nginx.md) | Image hai giai đoạn, mạng nội bộ, reverse proxy |

## Ghi bài mới

- Mỗi module mới: thêm **một file** vào đúng thư mục chủ đề (số thứ tự tiếp theo), hoặc nối vào bài đã có nếu là thay đổi của module đó.
- Cập nhật bảng mục lục ở trên và phần câu hỏi ôn tập bên dưới.

## Tổng hợp câu hỏi ôn tập

Để ôn một lượt cuối dự án — mỗi nhóm câu hỏi dẫn về bài gốc.

**[Nhập kho](1-kho-hang/01-nhap-kho.md)**

1. Nếu `lockForChange` kiểm tra trạng thái *trước* rồi mới gọi `findForUpdate`, chuyện gì xảy ra khi hai người cùng bấm "Hoàn tất"?
2. Vì sao `InventoryService` dùng `Propagation.MANDATORY` thay vì `REQUIRED` hay `REQUIRES_NEW`?
3. Đảo phiếu khi tồn không đủ bị chặn ở mấy lớp? Lớp nào vẫn chặn được nếu ai đó xóa câu `WHERE quantity >= :q`?
4. Vì sao ca test rollback giữa chừng không viết được trong lớp test có `@Transactional`?
5. `ON CONFLICT DO UPDATE` giải quyết vấn đề gì mà "SELECT xem có chưa → INSERT" không giải quyết được?

**[Nhập kho](1-kho-hang/01-nhap-kho.md)**

1. Nếu chỉ ẩn nút "Hoàn tất" ở frontend mà backend không khóa + kiểm tra trạng thái, có những cách nào để phiếu vẫn bị hoàn tất hai lần?
2. Vì sao ô sản phẩm lấy từ "SP đang gán cho NCC" thay vì tìm trong toàn bộ SP? Khi nào phải đổi cách làm?
3. "Lưu & hoàn tất" gọi 2 API liên tiếp (tạo nháp → hoàn tất). Nếu API thứ hai lỗi thì dữ liệu ở trạng thái nào, và giao diện cần hiện gì?

**[Tồn kho trên trang Sản phẩm](1-kho-hang/02-ton-kho-tren-san-pham.md)**

1. Vì sao `COALESCE(SUM(quantity), 0)` chứ không chỉ `SUM(quantity)` trong bộ lọc "Hết hàng"?
4. Trong JavaScript, `null !== undefined`, `null != undefined`, `null ?? 0` cho kết quả gì? Khi nào dùng `!=` thay vì `!==`?
5. Vì sao `@JsonInclude(NON_NULL)` trên `ApiResponse` không làm mất `"description": null` bên trong `data`?
2. Nếu đặt endpoint tồn kho trong module product thì phụ thuộc vòng xuất hiện ở đâu? Có những cách nào để tránh?
3. "Đang về" lấy SL đặt của phiếu Nháp. Khi phiếu đó hoàn tất với SL thực nhận khác SL đặt, con số nào thay đổi và thay đổi bao nhiêu?

**[Bán hàng, bảng giá theo khách, công nợ khách](1-kho-hang/03-ban-hang.md)**

1. Vì sao phiếu đã thu tiền thì không cho đảo? Nếu cho đảo, `paid_amount` và CHECK `paid <= total` sẽ ra sao?
2. Nếu bỏ khóa `FOR UPDATE` ở `recordPayment` thì vì sao CHECK `paid <= total` không cứu được? Viết lại câu cập nhật thế nào để DB tự chặn mà không cần khóa? (Gợi ý: `UPDATE … SET paid_amount = paid_amount + :x WHERE … AND paid_amount + :x <= total_amount`.)
3. Vì sao bảng giá không cần lưu lịch sử giá mà vẫn tra được "tháng trước bán cho khách X giá bao nhiêu"?

**[Lịch sử biến động tồn kho](1-kho-hang/04-lich-su-ton-kho.md)**

1. Vì sao chỉ lọc theo SP (không lọc kho) thì cột "Tồn trước" không nối liền với "Tồn sau" của dòng bên dưới?
2. Hai giao dịch của cùng một phiếu có `created_at` giống hệt nhau — vì sao? Hệ quả với việc sắp xếp?
3. Nếu ai đó chạy `UPDATE inventory SET quantity = 30` trực tiếp trên DB, trang lịch sử sẽ lộ ra điều đó ở đâu?

**[Tồn kho đầu kỳ](1-kho-hang/05-ton-dau-ky.md)**

1. Vì sao không `UPDATE inventory` thẳng khi khai tồn đầu kỳ? Lịch sử kho sẽ sai ở đâu?
2. Thêm một loại chứng từ vào bảng dùng chung cần kiểm tra điều gì trước để không làm hỏng báo cáo cũ?
3. SP đã nhập 10 bao rồi bán hết 10 (tồn = 0) — vì sao vẫn không cho khai đầu kỳ?
4. Hai lớp chặn trùng: lớp nào đủ khi chỉ một người dùng, lớp nào cần khi nhiều người bấm cùng lúc? Vì sao lớp 2 phải nằm sau câu upsert?

**[Kiểm kê kho](1-kho-hang/06-kiem-ke.md)**

1. Vì sao kiểm kê ghi chênh lệch thay vì đặt tồn bằng số đếm? Cho ví dụ số cụ thể.
2. Chụp số sổ sách lúc nào, khi nào phải chụp lại? Không khóa kho thì đổi lại phải chấp nhận điều gì?
3. Vì sao mỗi kho chỉ được một phiếu đang kiểm? Chuyện gì xảy ra nếu có hai phiếu cùng kiểm cám A?
4. Vì sao test rollback đặt trong lớp `@Transactional` lại cho kết quả sai?

**[Công nợ nhân viên: lương theo tấn, trả lương, tạm ứng](2-tien-va-cong-no/02-cong-no-nhan-vien.md)**

1. `ck_employee_advance_balance` đã có mà vẫn cần khóa `FOR UPDATE` trên nhân viên — vì sao? Cho kịch bản hai request cùng lúc làm lộ ra điều đó.
2. Vì sao trừ tạm ứng được ghi như một `method` của `payroll_payment` chứ không phải một bảng riêng? Hệ quả với báo cáo dòng tiền là gì?
3. Thứ tự khóa tháng → nhân viên → dòng lương ngăn deadlock như thế nào? Điều gì xảy ra nếu một thao tác khóa dòng lương trước nhân viên?
4. Vì sao hoàn một khoản tạm ứng có thể bị từ chối dù khoản đó chưa từng được hoàn?

**[Sổ quỹ — tiền mặt và tài khoản](2-tien-va-cong-no/03-so-quy.md)**

1. VIEW khác bảng ở điểm nào? Vì sao VIEW đủ cho sổ quỹ hiện tại nhưng không đủ nếu phải chặn quỹ âm?
2. Vì sao dòng "trừ tạm ứng" không xuất hiện trong sổ quỹ? Nếu có thì số dư sai thế nào?
3. Số dư lũy kế dùng `ORDER BY entry_date, created_at, source, source_id` — bỏ hai cột cuối thì có thể sai ở trường hợp nào?
4. Đầu kỳ sửa được còn phiếu thu/chi thì không — ranh giới giữa "cấu hình" và "chứng từ" là gì?

**[Công nợ đầu kỳ khách / NCC](2-tien-va-cong-no/04-cong-no-dau-ky.md)**

1. Vì sao khoản nợ cũ đặt trong `stock_document` thay vì bảng riêng? Cái giá phải trả là gì và bù lại bằng ràng buộc nào?
2. Partial unique index khác UNIQUE thường thế nào? Vì sao phải có `status = 'COMPLETED'` trong `WHERE`?
3. Đã có kiểm tra trùng trong service rồi, vì sao vẫn cần UNIQUE ở DB?
4. Migration đã chạy rồi mà cần sửa thì làm gì — khi nào được sửa file cũ, khi nào bắt buộc viết file mới?

**[Nhà cung cấp](3-danh-muc/01-nha-cung-cap.md)**

1. Nếu `product_supplier` dùng `id` tự tăng và **không** có UNIQUE `(supplier_id, product_id)`, hai người bấm "Gán" cùng lúc thì sao? Thiết kế hiện tại chặn ở đâu?
2. Vì sao truy vấn "SP Y do NCC nào cung cấp" không dùng được index của khóa chính?
3. Vì sao test `shouldSoftUnlink` fail lần đầu dù code service đúng?

**[Nhà cung cấp — chọn NCC từ form SP](3-danh-muc/01-nha-cung-cap.md)**

1. Vẽ mũi tên phụ thuộc giữa `product` và `supplier` trước và sau khi thêm `ProductSupplierLinks`. Bean nào được tiêm vào đâu lúc chạy?
2. Nếu `ProductSupplierLinkService` dùng `REQUIRES_NEW` thay vì `MANDATORY`, kịch bản nào để lại dữ liệu sai?
3. Vì sao test "NCC ngừng → SP không được tạo" vẫn viết được trong lớp test có `@Transactional`? Nếu `validate` chỉ chạy sau `save` thì test đó còn đúng không?
4. PUT không gửi `supplierIds` thì giữ nguyên liên kết. Đánh đổi gì so với quy ước "PUT = thay toàn bộ"?
5. Vì sao một bug chỉ xuất hiện khi "không có transaction" lại lọt qua toàn bộ test API? Viết test thế nào để bắt được?

**[Khách hàng](3-danh-muc/02-khach-hang.md)**

1. Vì sao NCC chặn trùng tên còn khách hàng thì chặn trùng SĐT? Khi nào cả hai cách đều sai?
2. Có `UNIQUE (phone)` mà không chuẩn hóa thì để lọt trường hợp nào? Vì sao còn cần thêm CHECK ở DB khi service đã chuẩn hóa rồi?
3. Trong PostgreSQL, sau một câu lệnh lỗi trong transaction thì câu tiếp theo nhận lỗi gì? SAVEPOINT giải quyết việc này ra sao?

**[Nhân viên, chấm công, bảng lương](3-danh-muc/03-nhan-vien-cham-cong-luong.md)**

1. Vì sao bảng lương đã chốt phải lưu đơn giá, chứ không JOIN sang `employee`?
2. `SELECT … FOR UPDATE` và `pg_advisory_xact_lock` khác nhau thế nào? Vì sao chốt lương dùng cái sau?
3. Nếu `AttendanceService.record` kiểm tra "đã chốt chưa" *trước* khi lấy khóa tháng thì kịch bản lỗi diễn ra thế nào?
4. Viết ràng buộc "mỗi tháng chỉ một bảng lương FINALIZED" bằng SQL.

**[Nhập hàng loạt từ Excel](3-danh-muc/04-nhap-excel.md)**

1. Đọc ô số Excel thành BigDecimal: `new BigDecimal(double)`, `BigDecimal.valueOf(double)` và `NumberToTextConverter` khác nhau thế nào? Cho ví dụ ô công thức.
2. `preview()` mà có `@Transactional` thì người dùng thấy gì khi file có một tên trùng? Vì sao?
3. Vì sao bước "Nhập" phải kiểm tra lại từ đầu dù vừa preview không có lỗi?
4. Dropdown trong Excel đã giới hạn giá trị, tại sao backend vẫn phải kiểm?

**[Cấu hình sản phẩm: danh mục và đơn vị tính](3-danh-muc/05-cau-hinh-san-pham.md)**

1. Khi nào nên dùng `enum` + `CHECK`, khi nào nên dùng bảng danh mục + FK? Cho một ví dụ trong dự án cho mỗi cách.
2. Vì sao không viết thẳng `ADD COLUMN unit_id BIGINT NOT NULL REFERENCES product_unit`? Migration sẽ lỗi ra sao trên DB đang có sản phẩm?
3. `Product.unit` để EAGER, `Product.category` để LAZY. Nếu `category` cũng để EAGER thì có hại gì không? Còn `StockDocumentItem.product` thì sao?
4. Vì sao API đơn vị không nhận `fixedNetWeightKg`, dù gửi lên cũng chẳng gây lỗi gì?
5. Đổi tên đơn vị "Bao" thành "Bao 25kg" thì phiếu nhập tháng trước hiển thị gì? Muốn giữ tên cũ thì phải đổi schema thế nào?

**[Đăng nhập & phân quyền](4-nen-tang-he-thong/01-dang-nhap-phan-quyen.md)**

1. Vì sao lưu token trong `localStorage` thì không cần chống CSRF, còn lưu trong cookie thì bắt buộc? Đổi lại, `localStorage` mất gì?
2. Quản lý hệ thống khóa tài khoản lúc 9:00. Đến lúc nào người đó mới thật sự mất quyền? Muốn tức thì thì phải đánh đổi gì?
3. Kẻ gian chép được refresh token của chị Lan lúc 9:00. Mô tả chuyện xảy ra nếu chị Lan gia hạn lúc 9:05 và kẻ gian dùng token lúc 10:00.
4. Vì sao `AuthorizationMatrixTest` kiểm cả "sai vai trò → 403" chứ không chỉ "đúng vai trò → 200"?

**[Bộ lọc dùng chung và giao diện danh sách](4-nen-tang-he-thong/02-bo-loc-dung-chung-va-ui.md)**

1. Skeleton loading khác spinner thế nào? Vì sao skeleton làm người dùng cảm thấy trang tải nhanh hơn?
2. Vì sao chỉ hiện thông báo thành công **sau khi** API trả `success: true`, không hiện ngay lúc bấm nút?

**[Bộ lọc dùng chung và giao diện danh sách](4-nen-tang-he-thong/02-bo-loc-dung-chung-va-ui.md)**

1. Vì sao không cho client gửi tên cột tùy ý để lọc, dù làm vậy "chung" hơn?
2. Vì sao không tạo được index trên `lower(unaccent(name))` nhưng tạo được trên `lower(immutable_unaccent(name))`?
3. "Mở lại" gửi PUT với dữ liệu của dòng trong bảng. Nếu người khác vừa sửa tên NCC đó, chuyện gì xảy ra? Cần gì để phát hiện (gợi ý: optimistic locking / `version`)?

**[Mã tự sinh](4-nen-tang-he-thong/03-ma-tu-sinh.md)**

1. Vì sao sequence không rollback theo transaction? Nếu nó rollback được thì hai transaction song song sẽ gặp chuyện gì?
2. Sửa một migration đã chạy thì Flyway phản ứng thế nào? Khi nào `flyway repair` là chấp nhận được?

**[In phiếu (K80)](4-nen-tang-he-thong/04-in-phieu.md)**

1. Nếu sau này cần lại phiếu cho lái xe không có giá thì làm ở đâu: backend hay mẫu in? Vì sao?
2. Nếu cho in khi form nháp chưa lưu, kịch bản nào làm lệch giữa hàng thực giao và sổ sách?
3. Đọc thành chữ: 1.000.005, 2.000.001.000, 21, 11, 105. Giải thích "không trăm", "linh", "mốt".
4. Vì sao dùng iframe chứ không `window.print()` thẳng trên trang?
5. `tsc --noEmit -p .` báo thành công mà không kiểm tra gì: vì sao? Làm sao biết một lệnh kiểm tra có thực sự hoạt động?

**[Bản chất deploy](5-van-hanh/01-ban-chat-deploy.md)**

1. Code, cấu hình, dữ liệu — cái nào nằm trong git, cái nào không, cái nào mất là mất thật?
2. Vì sao từ Internet không kết nối thẳng được vào PostgreSQL dù DB đang chạy trên cùng server?
3. Khi chạy `up -d --build`, container backend bị thay mới — vì sao dữ liệu vẫn còn?
4. Deploy V22 xong phát hiện lỗi nghiêm trọng, muốn quay về bản cũ: các bước là gì, và vì sao chỉ `git checkout` là không đủ?

**[Đóng gói & triển khai: Docker Compose + Nginx](5-van-hanh/02-docker-compose-nginx.md)**

1. Vì sao `docker compose down -v` nguy hiểm còn `down` thì không?
2. Bỏ `try_files $uri $uri/ /index.html` thì người dùng gặp lỗi gì, ở thao tác nào?
3. Vì sao Spring Boot gọi được `jdbc:postgresql://db:5432/...` dù không có máy nào tên `db` trong mạng LAN?
4. Lộ `JWT_SECRET` nguy hiểm hơn lộ mật khẩu admin ở điểm nào? Phải làm gì ngay khi bị lộ?
5. Sửa `POSTGRES_PASSWORD` trong `.env.prod` rồi `up -d` thì chuyện gì xảy ra, vì sao?
6. Vì sao Let's Encrypt không cấp được chứng chỉ khi cổng 80 bị chặn?
