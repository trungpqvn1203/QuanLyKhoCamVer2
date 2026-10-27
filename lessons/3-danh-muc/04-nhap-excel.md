# Nhập hàng loạt từ Excel

[← Mục lục kho bài học](../README.md)

## Module: Nhập hàng loạt từ Excel (2026-10-24, ADR-0004)

### Khái niệm

- **File `.xlsx` là một file zip chứa các file XML.** Apache POI giải nén và đọc các XML đó thành `Workbook → Sheet → Row → Cell`. Vì thế đổi đuôi `.csv` thành `.xlsx` thì POI vẫn ném lỗi. Kẻ xấu có thể gửi một file nhỏ nhưng giải nén ra hàng GB ("zip bomb"); POI mặc định chặn file có tỉ lệ nén bất thường.
- **Excel lưu mọi con số dưới dạng `double`.** Gõ `25000.1` thì trong file là số nhị phân xấp xỉ. `new BigDecimal(25000.1)` ra `25000.100000000002182787…`. `BigDecimal.valueOf(double)` đúng với số gõ tay, nhưng ô công thức `=0.1+0.2` lại ra `0.30000000000000004`, trong khi Excel hiển thị `0.3` (Excel chỉ dùng 15 chữ số có nghĩa). Ở đây dùng `NumberToTextConverter.toText()` để lấy đúng chuỗi Excel hiển thị, rồi mới `new BigDecimal(String)`. Đây là quy tắc cứng số 3 (tiền dùng BigDecimal) áp dụng ở biên vào của dữ liệu.
- **Ô định dạng Text và số 0 đầu.** Gõ `0901234567` vào ô kiểu General thì Excel hiểu là số `901234567` và mất số 0. File mẫu đặt sẵn định dạng `@` (Text) cho cột SĐT bằng `setDefaultColumnStyle`. Đây là lý do **file mẫu do hệ thống sinh** tốt hơn để người dùng tự tạo file.
- **Dropdown (Data Validation) lấy danh sách từ sheet ẩn.** Liệt kê thẳng `"Bao,Kg"` chỉ được tối đa 255 ký tự, vài chục tên danh mục là vượt. Vì vậy danh sách được ghi vào sheet `_meta` (VERY_HIDDEN, không hiện trong menu Unhide) và dropdown trỏ tới vùng `'_meta'!$C$1:$C$n`. Dropdown chỉ là **gợi ý**: người dùng dán đè vẫn lọt, nên backend vẫn kiểm từng giá trị.
- **Dùng lại quy tắc thay vì viết lại.** `create()` của mỗi service được tách thành `prepareNew(request)` (kiểm tra + dựng entity chưa lưu) và `save()`. Form nhập tay và import cùng gọi `prepareNew`, và mỗi dòng Excel được ép thành đúng `CreateProductRequest`, nên đi qua cùng `@NotBlank`, `@Digits`. Ví dụ "đơn vị Kg phải 1 kg" (BR-PRD-06) chỉ được viết **một lần**. Nếu viết riêng cho import, sớm muộn hai bên sẽ lệch nhau.
- **"Tất cả hoặc không gì" = kiểm hết trước, ghi sau, trong một transaction.** `commit()` gom lỗi của cả 1.000 dòng trước, rồi mới gọi `saveAll`. Có lỗi thì ném exception trước khi có câu INSERT nào. Nếu UNIQUE ở DB chặn lúc ghi (có người nhập trùng cùng lúc) thì `@Transactional` rollback cả các dòng đã INSERT.
- **Exception nghiệp vụ trong transaction lồng nhau → "rollback-only".** `preview()` cố ý **không** có `@Transactional`. Lý do: khi `productService.prepareNew()` (có `@Transactional` ở class) ném `DuplicateResourceException` bên trong transaction của người gọi, Spring đánh dấu cả transaction ngoài là rollback-only. Hàm ngoài bắt exception, gom lỗi, rồi trả kết quả bình thường. Đến lúc commit, Spring ném `UnexpectedRollbackException` và danh sách lỗi không bao giờ tới người dùng. Ở `commit()` thì không sao, vì có lỗi là ta ném exception (rollback) ngay.
- **Lỗi phải chỉ được cho người dùng chỗ cần sửa.** Lỗi trả về theo số dòng **Excel** (tiêu đề là dòng 1, POI đếm từ 0 nên phải +1) kèm tên cột. Mỗi cột giữ lỗi đầu tiên: ô `"25kg"` báo "Phải là số", không báo thêm "không được để trống" (lỗi phụ sinh ra vì field bị gán null).

### Lỗi đã gặp

- Chạy cả bộ test: `UserApiTest.shouldKeepAtLeastOneActiveAdmin` thất bại (mong 409, nhận 200). Chạy lại trên code `main` (stash thay đổi) cũng thất bại → **lỗi có từ trước**, không do module này. Nghi DB test `grain_warehouse_test` còn sẵn một Quản lý hệ thống đã commit (ví dụ do chạy app bằng DB test), nên "người cuối cùng" thực ra không phải người cuối. Chưa sửa, chờ xác nhận.
- Thử đột biến: đảo thứ tự trong `commit()` (ghi trước, kiểm lỗi sau) → `shouldImportNothingWhenAnyRowFails` fail (mong 0 SP mới, thực tế 2). Như vậy test bắt đúng lỗi "nhập dở dang".

### Câu hỏi ôn tập (để cuối dự án)

1. Đọc ô số Excel thành BigDecimal: `new BigDecimal(double)`, `BigDecimal.valueOf(double)` và `NumberToTextConverter` khác nhau thế nào? Cho ví dụ ô công thức.
2. `preview()` mà có `@Transactional` thì người dùng thấy gì khi file có một tên trùng? Vì sao?
3. Vì sao bước "Nhập" phải kiểm tra lại từ đầu dù vừa preview không có lỗi?
4. Dropdown trong Excel đã giới hạn giá trị, tại sao backend vẫn phải kiểm?
