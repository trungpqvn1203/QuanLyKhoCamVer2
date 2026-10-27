# Mã tự sinh

[← Mục lục kho bài học](../README.md)

## Sửa lỗi: mã tự sinh bị cắt cụt (2026-10-20, migration V10)

- **Triệu chứng**: đang làm module Nhân viên thì chạy test, 20 test của các module *cũ* (kho, NCC, tồn kho) đột nhiên báo `duplicate key uk_warehouse_code: (code)=(KHO105)`. Đây không phải lỗi của code vừa viết.
- **Nguyên nhân**: `lpad(text, n, '0')` **không chỉ đệm mà còn cắt** chuỗi dài hơn n ký tự. `'KHO' || lpad('1050', 3, '0')` cho ra `KHO105`, và mã 1051…1059 cũng cho ra `KHO105` → trùng. Lỗi nằm sẵn từ V2, nhưng chỉ lộ khi sequence vượt 999.
- **Vì sao DB test lộ trước DB thật**: test rollback dữ liệu, nhưng **sequence không rollback**. Gọi `nextval` thì giá trị đó mất vĩnh viễn, để hai transaction chạy song song không bao giờ nhận cùng một số. Sau vài chục lần `mvn test`, `warehouse_code_seq` trong DB test đã lên 1056, còn DB dev mới ở 10. Ở kho thật, lỗi sẽ xuất hiện khi tạo NCC thứ 1000.
- **Mình đã ghi sai trong V8**: "quá 9999 thì mã dài thêm". Mình viết mà không kiểm chứng. Bài học: hành vi của hàm thư viện phải chạy thử bằng một câu `SELECT` trước khi ghi vào tài liệu. Cũng chính câu `SELECT 'KHO' || lpad('1050',3,'0')` đã xác nhận nguyên nhân chỉ trong một giây.
- **Cách sửa: migration mới, không sửa migration cũ**. Flyway lưu checksum của từng file đã chạy; sửa V3 thì mọi DB đã chạy V3 sẽ báo `Validate failed` khi khởi động. V10 tạo hàm `format_code(prefix, n, width)`: đệm số 0 khi số ngắn hơn width, giữ nguyên khi dài hơn. Sau đó V10 đổi `DEFAULT` của 5 cột mã. Mã đã có giữ nguyên.
- **Test chặn tái phát**: `CodeGenerationPersistenceTest` đọc `DEFAULT` của mọi cột `code` trong `pg_attrdef` và bắt buộc phải dùng `format_code`. Bảng mới mà ai đó lại viết `lpad` thì test fail. Test kiểm hàm trực tiếp chứ không dùng `setval`, vì `setval` cũng không rollback, sẽ đẩy sequence thật lên mãi.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao sequence không rollback theo transaction? Nếu nó rollback được thì hai transaction song song sẽ gặp chuyện gì?
2. Sửa một migration đã chạy thì Flyway phản ứng thế nào? Khi nào `flyway repair` là chấp nhận được?
