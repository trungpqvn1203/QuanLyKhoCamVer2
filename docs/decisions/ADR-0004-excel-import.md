# ADR-0004: Nhập hàng loạt danh mục từ Excel

## Trạng thái

✅ Đã chấp nhận — 2026-10-24

## Bối cảnh

Hiện mỗi sản phẩm, khách hàng, nhà cung cấp, nhân viên phải nhập tay qua một popup. Khi bắt đầu dùng hệ thống, cần đưa vào hàng trăm bản ghi đang nằm trong Excel. Ngoài ra, người nhập cần dùng **một mẫu thống nhất**, không mỗi người một kiểu file.

## Quyết định

1. **Định dạng `.xlsx`, đọc và sinh bằng Apache POI (`poi-ooxml` 5.4.0).** Đây là thư viện duy nhất được thêm vào.
2. **File mẫu do backend sinh ra**, không để file tĩnh. Nhờ vậy dropdown "Danh mục" luôn khớp các danh mục đang dùng trong DB. Mã mẫu (`PRODUCT_v1`…) nằm trong sheet ẩn `_meta` để chặn nhầm loại mẫu và mẫu cũ.
3. **Hai bước, không giữ trạng thái trên server:** `preview` kiểm tra và trả lỗi; `import` nhận lại chính file đó, kiểm tra lại từ đầu rồi ghi trong **một transaction**, gặp lỗi thì không ghi gì.
4. **Không viết lại quy tắc nghiệp vụ cho import.** Mỗi service tách hàm `prepareNew(request)`: kiểm tra quy tắc rồi dựng entity *chưa lưu*. `create()` của form nhập tay và import đều gọi hàm này. Mỗi dòng Excel được ép kiểu thành đúng `CreateXxxRequest` của form, nên cũng đi qua cùng Bean Validation.
5. Phần chung nằm ở `common/importing` (`ExcelImportService`, `ExcelWorkbooks`, `ImportRow`). Mỗi module chỉ cài một bean `XxxImportDefinition`: khai báo cột, ép kiểu, khóa so trùng.

## Phương án đã cân nhắc

| Phương án | Vì sao không chọn (lúc này) |
|---|---|
| **CSV**, không thêm thư viện | Không có dropdown. Excel mở CSV hay làm mất số 0 đầu SĐT và hỏng tiếng Việt nếu không lưu "CSV UTF-8". Như vậy không đáp ứng yêu cầu "mẫu chung, khó điền sai" |
| **Thư viện bọc POI** (EasyExcel, Poiji…) | Thêm một lớp phụ thuộc cho nhu cầu nhỏ (4 mẫu, tối đa 1.000 dòng). POI trực tiếp đủ dùng và giúp hiểu rõ cấu trúc file |
| **Lưu kết quả preview trên server** (token), commit không cần gửi lại file | Phải lưu trạng thái, có hạn dùng và cần dọn dẹp. Hơn nữa vẫn phải kiểm tra lại lúc commit vì dữ liệu có thể đã đổi. Chỉ đáng làm khi file rất lớn |
| **Ghi từng dòng hợp lệ, bỏ qua dòng lỗi** | Người dùng phải tự dò xem dòng nào đã vào; sửa xong nhập lại thì dễ trùng. "Tất cả hoặc không gì" dễ hiểu hơn |
| **Dán từ Excel vào bảng trên web** | Cần nhiều giao diện hơn. Có thể làm sau và dùng lại cùng backend (`prepareNew` + kiểm tra theo dòng) |

## Hệ quả

- Thêm khoảng 15–18 MB dependency (POI, XMLBeans, commons-compress, commons-math3…) vào jar backend.
- POI đọc cả file vào bộ nhớ (`XSSFWorkbook`). Với giới hạn 2 MB và 1.000 dòng thì không sao; file lớn hơn nhiều sẽ phải chuyển sang đọc kiểu streaming (SAX).
- File `.xlsx` thực chất là file zip. POI mặc định từ chối file có tỉ lệ nén bất thường ("zip bomb"). Giới hạn 2 MB ở `spring.servlet.multipart` là lớp chặn thứ hai (Nginx cho tối đa 5 MB).
- Thêm cột vào một mẫu thì **tăng phiên bản** mã mẫu (`PRODUCT_v2`) để file mẫu cũ bị từ chối rõ ràng, thay vì bị đọc lệch cột.
