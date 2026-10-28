# Khách hàng

[← Mục lục kho bài học](../README.md)

## Module: Khách hàng (2026-10-20)

- **Chọn khóa chống trùng theo nghiệp vụ, không theo thói quen** — NCC chặn trùng tên, còn khách thì *không*. Lý do: hai trang trại khác xã có thể cùng tên "Trại heo Minh", nhưng hai khách không dùng chung một số điện thoại. Vì vậy `uk_customer_phone` là UNIQUE, còn tên thì được trùng. Nếu chặn trùng tên, người bán sẽ phải đặt tên giả kiểu "Trại heo Minh 2". Nếu chẳng chặn gì, một khách sẽ thành hai mã, và báo cáo doanh số theo khách bị chia đôi.
- **UNIQUE chỉ có tác dụng khi dữ liệu đã được chuẩn hóa** — UNIQUE so sánh từng ký tự, nên `0901 234 567` và `0901234567` được coi là hai giá trị khác nhau và lọt qua. Cách làm ở đây là *chuẩn hóa trước, rồi mới kiểm định dạng, kiểm trùng và lưu*. Ngoài ra còn có `CHECK (phone ~ '^0[0-9]{9,10}$')` để kể cả code khác (script seed, SQL tay) cũng không lưu được dạng chưa chuẩn hóa. Phương án khác là lưu nguyên văn và tạo `UNIQUE INDEX` trên biểu thức `regexp_replace(phone, '[ .-]', '', 'g')`. Cách đó giữ được cách gõ của người dùng, nhưng mọi câu tìm kiếm cũng phải dùng đúng biểu thức ấy.
- **Vì sao không dùng `@Pattern` cho SĐT** — Bean Validation chạy trên chuỗi *thô*, nên `@Pattern("^0\d{9,10}$")` sẽ từ chối `0901 234 567`, một cách gõ hợp lệ. Định dạng vì vậy được kiểm ở service sau bước chuẩn hóa. Khi sai thì ném `InvalidRequestException("phone", ...)`, frontend nhận cùng dạng 400 `details[{field}]` như mọi lỗi validation khác và tô đỏ đúng ô.
- **Kiểm trùng ở service + ràng buộc ở DB, cả hai đều cần** — service tìm khách đang giữ SĐT để báo lỗi *có ích* ("thuộc về khách 'Trại heo Minh' (KH0003), đang ngừng → hãy mở lại"). Tuy vậy, nếu hai request cùng SĐT đến đồng thời thì cả hai có thể cùng thấy "chưa có". Khi đó UNIQUE ở DB chặn request đến sau, và GlobalExceptionHandler trả 409.

### Lỗi đã gặp: test DB kiểm hai vi phạm trong một transaction

- Test kiểm `ck_customer_phone` gồm hai câu INSERT sai liên tiếp. Câu thứ hai không trả về lỗi CHECK mà trả `25P02 current transaction is aborted`. Nguyên nhân: sau lỗi đầu tiên, **PostgreSQL bỏ cả transaction** và từ chối mọi lệnh tiếp theo cho đến khi ROLLBACK. Test chạy trong `@Transactional` nên cả hai câu nằm chung một transaction. Cách sửa là tách mỗi vi phạm thành một test (hoặc dùng SAVEPOINT). Hành vi này khác với MySQL: ở MySQL, một câu lỗi không kéo theo cả transaction.
- Đã thử đột biến: cho `normalize` trả nguyên chuỗi thì 3 test API fail (lưu dạng chuẩn hóa, chặn trùng khi gõ khác cách, sửa khách).

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao NCC chặn trùng tên còn khách hàng thì chặn trùng SĐT? Khi nào cả hai cách đều sai?
2. Có `UNIQUE (phone)` mà không chuẩn hóa thì để lọt trường hợp nào? Vì sao còn cần thêm CHECK ở DB khi service đã chuẩn hóa rồi?
3. Trong PostgreSQL, sau một câu lệnh lỗi trong transaction thì câu tiếp theo nhận lỗi gì? SAVEPOINT giải quyết việc này ra sao?
