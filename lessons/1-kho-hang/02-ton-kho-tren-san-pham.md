# Tồn kho trên trang Sản phẩm

[← Mục lục kho bài học](../README.md)

## Module: Tồn kho trên trang Sản phẩm (2026-10-19)

- **Hướng phụ thuộc quyết định chỗ đặt code** — `inventory` đã dùng `Product` (dòng tồn trỏ tới SP). Nếu `ProductService` gọi ngược `InventoryRepository` để lấy số tồn, hai module phụ thuộc vòng: sửa module nào cũng phải biên dịch lại module kia, và không tách riêng được. Vì vậy API "SP kèm tồn" đặt ở `inventory` (`/api/inventory/products`), dùng lại nguyên `ProductSpecifications`. Phương án khác là *đảo phụ thuộc*: `product` khai báo interface `ProductStockReader`, `inventory` cài đặt nó. Cách đó đúng hơn khi product *bắt buộc* phải trả số tồn, nhưng thêm một lớp trừu tượng.
- **Tránh N+1 bằng GROUP BY theo trang** — trang 20 SP mà mỗi SP chạy một câu `SUM` thì thành 1 + 20 câu. Ở đây chỉ chạy một câu `SUM … WHERE product_id IN (20 id) GROUP BY product_id`, rồi ghép theo id trong Java. Số câu SQL cố định, không phụ thuộc số SP trên trang.
- **Lọc theo số tính toán phải làm trong SQL** — "Sắp hết" phụ thuộc tổng tồn, mà tổng không phải một cột. Nếu lọc sau khi tải trang thì tổng số kết quả và phân trang sai (giống bài học bộ lọc trước). Giải pháp là subquery trong `Specification`: `(SELECT COALESCE(SUM(quantity),0) FROM inventory WHERE product_id = p.id) <= p.min_stock_quantity`. `COALESCE` cần cho SP chưa từng nhập (SUM của 0 dòng là NULL, không phải 0).
- **Một quy tắc viết hai nơi thì phải có test giữ chúng khớp** — `StockStatus.of` (Java, để trả về) và `hasStockStatus` (SQL, để lọc) cùng diễn đạt "≤ mức tối thiểu". Đã thử đổi `≤` thành `<` trong SQL → `shouldFilterByStockStatus` fail (Cám heo tồn đúng 50 = mức 50 không còn được lọc ra là Sắp hết).
- **PUT = thay toàn bộ → thêm trường là phải rà mọi chỗ gọi PUT** — nút "Mở bán lại" tự dựng body PUT từ dữ liệu dòng. Thêm `minStockQuantity` vào API mà quên thêm ở đây thì mỗi lần mở bán lại sẽ âm thầm xóa mức tồn tối thiểu. `grep "productApi.update"` để tìm hết các chỗ.
- **Tải khi cần (lazy) cho popover** — 20 dòng × tồn từng kho mà tải sẵn thì tốn 20 request, trong khi người dùng thường chỉ rê chuột vài dòng. Gọi API ở `onOpenChange` lần đầu, rồi giữ kết quả. Đặt `key` theo số tồn để dữ liệu cũ không bị giữ sau khi danh sách tải lại.

### Lỗi đã gặp: không chọn kho mà ô tồn vẫn hiện 0, dưới ghi "Tổng mọi kho: 27"

- **Nguyên nhân gốc: backend trả `"warehouseId": null` thay vì bỏ trường**, trái với contract (NON_NULL). `@JsonInclude(NON_NULL)` chỉ gắn trên `ApiResponse`; annotation trên một lớp **không lan** sang các object lồng bên trong (`data`). Lỗi này có từ đầu dự án, ở mọi API (`"description": null`, `"email": null`...). Sửa ở một chỗ: `spring.jackson.default-property-inclusion: non_null`.
- **Nguyên nhân trực tiếp ở frontend**: `product.warehouseId !== undefined` mà `null !== undefined` là `true` → giao diện tưởng đang lọc kho, lấy `warehouseStockQuantity ?? 0` = 0. Với dữ liệu từ API, dùng `!= null` / `== null` (bắt cả `null` lẫn `undefined`). TypeScript khai báo `warehouseId?: number` nhưng **không kiểm tra lúc chạy**: kiểu chỉ là lời hứa, JSON thật có thể khác.
- **Vì sao test không bắt được**: `jsonPath("$.x").doesNotExist()` vẫn pass khi `x` có giá trị `null`. Test mới `JsonNullOmissionTest` kiểm tra trên **chuỗi JSON thô** (không có `"field": null`). Đã thử bỏ dòng cấu hình → 4/6 API fail.
- **Bài học UX**: ô số mà không ghi phạm vi ("tại kho nào") thì hai con số đúng vẫn trông như mâu thuẫn. Tiêu đề cột giờ đổi thành "Tồn tại KHO002" khi đang chọn kho.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao `COALESCE(SUM(quantity), 0)` chứ không chỉ `SUM(quantity)` trong bộ lọc "Hết hàng"?
4. Trong JavaScript, `null !== undefined`, `null != undefined`, `null ?? 0` cho kết quả gì? Khi nào dùng `!=` thay vì `!==`?
5. Vì sao `@JsonInclude(NON_NULL)` trên `ApiResponse` không làm mất `"description": null` bên trong `data`?
2. Nếu đặt endpoint tồn kho trong module product thì phụ thuộc vòng xuất hiện ở đâu? Có những cách nào để tránh?
3. "Đang về" lấy SL đặt của phiếu Nháp. Khi phiếu đó hoàn tất với SL thực nhận khác SL đặt, con số nào thay đổi và thay đổi bao nhiêu?
