# Cấu hình sản phẩm: danh mục và đơn vị tính

[← Mục lục kho bài học](../README.md)

## Module: Cấu hình sản phẩm (2026-10-28)

- **API có mà màn hình không có thì tính năng coi như chưa có** — backend có CRUD danh mục từ V2 (có test), nhưng frontend chỉ *đọc* danh mục để đổ vào ô chọn. Trên máy dev không ai nhận ra vì script seed đã tạo sẵn "Cám heo", "Cám gà". Lên server với DB mới thì bảng `category` rỗng, mà danh mục là trường bắt buộc, nên **không tạo được sản phẩm nào**. Bài học: kiểm tra một luồng phải bắt đầu từ DB trống. Dữ liệu seed che mất những bước thiếu.
- **Chuỗi có CHECK hay bảng danh mục?** — Ban đầu `unit` là `VARCHAR` với `CHECK (unit IN ('BAG','KG'))` và Java có `enum ProductUnit`. Cách này hợp lý khi danh sách cố định *và chỉ lập trình viên sửa*: thêm đơn vị = viết migration + deploy. Khi người dùng muốn tự thêm "Túi", "Thùng", danh sách trở thành *dữ liệu*, nên phải chuyển sang bảng `product_unit` + FK. Dấu hiệu nhận biết: nếu người dùng cuối cần thêm hoặc sửa giá trị thì đó là dữ liệu (bảng); nếu chỉ code mới được đổi thì đó là cấu hình (enum/CHECK).
- **Thay `if (unit == KG)` bằng dữ liệu** — Luật cũ "đơn vị KG thì khối lượng tịnh = 1" được viết cứng trong 3 chỗ (service, form, import Excel). Có bảng rồi, luật thành cột `fixed_net_weight_kg`: Kg = 1, đơn vị khác để NULL. Service chỉ còn một luật chung: *có giá trị cố định thì khối lượng phải bằng nó*. Code không còn biết tên "Kg". Nhờ vậy người dùng đổi tên "Kg" thành "Ký" thì luật vẫn đúng. Cột này không có trong request API, entity cũng không có setter, nên người dùng không tạo được đơn vị "1 thùng = 20 kg" và phá vỡ luật.
- **Migration chuyển dữ liệu 4 bước** — thêm cột `unit_id` cho phép NULL → `UPDATE … FROM product_unit` theo bảng ánh xạ `BAG → Bao`, `KG → Kg` → `SET NOT NULL` + FK → bỏ cột cũ. Không thể thêm thẳng cột `NOT NULL`: các SP đang có không có giá trị nào để điền vào. Cả 4 bước nằm trong một file migration, mà PostgreSQL chạy DDL trong transaction, nên nếu một dòng không ánh xạ được thì `SET NOT NULL` lỗi và **toàn bộ** migration rollback, server giữ nguyên trạng thái cũ.
- **LAZY hay EAGER cho `Product.unit`** — `category` là LAZY và mọi truy vấn cần tên danh mục đều phải `@EntityGraph`/`JOIN FETCH`. Tên đơn vị thì có mặt trong khoảng 10 response (dòng phiếu nhập, bán, đầu kỳ, kiểm kê, lịch sử kho, bảng giá…). Dự án tắt `open-in-view`, nên nếu để LAZY mà quên JOIN FETCH ở một chỗ thì sẽ gặp `LazyInitializationException` lúc dựng response. Ta chọn EAGER vì bảng đơn vị chỉ có vài dòng: Hibernate nạp mỗi đơn vị **một lần** cho mỗi persistence context, nên 100 SP dùng chung "Bao" vẫn chỉ tốn 1 SELECT, không thành N+1 theo số SP. Nếu bảng lớn (ví dụ `customer`), EAGER sẽ là lựa chọn sai.
- **Đổi hợp đồng API không tương thích ngược** — `unit: "BAG"` đổi thành `unitId` + `unitName` ở mọi response. Backend và frontend nằm chung một repo, deploy cùng lúc, nên đổi thẳng được. Nếu có client khác (app di động) thì phải giữ cả hai field trong một thời gian. Frontend bỏ được bảng nhãn `PRODUCT_UNIT_LABELS`: tên lấy thẳng từ DB nên chỉ có một nguồn sự thật.
- **Gộp cấu hình vào một Drawer, không thêm menu** — danh mục và đơn vị là "dữ liệu phụ" của sản phẩm, ít khi sửa. Đặt sau nút **Cấu hình** trên trang Sản phẩm thì navbar không dài thêm mà người dùng vẫn tìm được ngay nơi cần dùng. Hai tab dùng chung component `CatalogPanel` (thêm, đổi tên bằng cách bấm vào tên, bật/tắt đang dùng).

### Lỗi đã gặp

- **Script sửa test tự động sửa nhầm một chỗ** — dùng regex `unit: (product).unit → unit: product.unitName` cho các form phiếu. Regex này cũng khớp với hàm "Mở bán lại" trên trang Sản phẩm, nơi cần gửi `unitId` chứ không phải tên. Lỗi này được phát hiện khi grep lại các dòng vừa đổi, trước khi chạy `tsc` (`tsc` cũng sẽ bắt được vì thiếu `unitId`, nhưng regex tương tự trên field kiểu `string` thì không). Bài học: sau khi thay thế hàng loạt, grep lại từng chỗ bị đổi, không tin hoàn toàn vào kết quả compile.
- **Lint `set-state-in-effect`** — gọi `setChanged(false)` và `setLoading(true)` *ngay trong* `useEffect` khiến React render thêm một lượt. Cách sửa: reset `changed` trong handler đóng Drawer (đúng sự kiện gây ra thay đổi); dùng `null` làm trạng thái "chưa tải" thay cho cờ `loading` riêng.

### Câu hỏi ôn tập (để cuối dự án)

1. Khi nào nên dùng `enum` + `CHECK`, khi nào nên dùng bảng danh mục + FK? Cho một ví dụ trong dự án cho mỗi cách.
2. Vì sao không viết thẳng `ALTER TABLE product ADD COLUMN unit_id BIGINT NOT NULL REFERENCES product_unit`? Migration sẽ lỗi ra sao trên DB đang có sản phẩm?
3. `Product.unit` để EAGER, `Product.category` để LAZY. Nếu `category` cũng để EAGER thì có hại gì không? Còn `StockDocumentItem.product` thì sao?
4. Vì sao API đơn vị không nhận `fixedNetWeightKg`, dù gửi lên cũng chẳng gây lỗi gì?
5. Đổi tên đơn vị "Bao" thành "Bao 25kg" thì phiếu nhập tháng trước hiển thị gì? Muốn giữ tên cũ thì phải đổi schema thế nào?
