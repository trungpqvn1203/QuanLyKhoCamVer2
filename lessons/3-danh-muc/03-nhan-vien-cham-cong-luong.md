# Nhân viên, chấm công, bảng lương

[← Mục lục kho bài học](../README.md)

## Module: Nhân viên, chấm công, bảng lương (2026-10-20)

- **Bản chụp (snapshot) thay vì tham chiếu** — bảng lương đã chốt lưu luôn *đơn giá lúc chốt* vào `payroll_item`, thay vì JOIN sang `employee.daily_wage` mỗi khi xem. Ví dụ: tháng 9 anh Bốc hưởng 52.500đ/tấn, tháng 10 được tăng lên 55.000đ. Nếu chỉ tham chiếu, lương tháng 9 đã trả sẽ tự đổi thành tiền theo giá mới. Đây cũng là lý do phiếu nhập lưu `unit_price` trên từng dòng. Nguyên tắc chung: **con số đã dùng để trả tiền thì phải được chụp lại tại thời điểm trả**.
- **Advisory lock của PostgreSQL** — `SELECT ... FOR UPDATE` chỉ khóa được *dòng đã có*. Chốt lương lại cần chặn cả việc *thêm* ô chấm công mới vào tháng đó, mà ô chưa có thì không có dòng nào để khóa. `pg_advisory_xact_lock(4201, 202610)` khóa một "con số" do ta tự quy ước (ở đây là loại khóa + yyyymm), và tự nhả khi transaction kết thúc. Khóa chỉ có tác dụng khi **mọi đường ghi** (chấm công, xóa ô, chốt, hủy chốt) đều lấy nó: giống bài học FOR UPDATE ở phiếu nhập, chỉ cần một đường quên khóa là hỏng.
- **Khóa rồi mới kiểm tra** — `isPayrollFinalized()` chỉ cho kết quả đúng khi gọi *sau* `lock()`. Nếu kiểm tra trước, chốt lương có thể commit ngay giữa lúc kiểm tra xong và lúc ghi. Đã thử bỏ khóa trong `finalizeMonth`: `PayrollConcurrencyTest` fail ở cả 2 lần chạy, bản chụp ghi 2 tấn trong khi DB có 6 tấn, tức là 4 ô chấm công đã lọt vào sau khi chụp.
- **Index một phần (partial unique index)** — "mỗi tháng chỉ một bảng lương còn hiệu lực, nhưng được giữ nhiều bản đã hủy" không viết được bằng `UNIQUE (period)`. Cách làm là `CREATE UNIQUE INDEX ... ON payroll (period) WHERE status = 'FINALIZED'`: chỉ các dòng thỏa `WHERE` mới tham gia ràng buộc.
- **Tránh phụ thuộc vòng giữa module** — payroll đọc chấm công để tính lương (payroll → attendance), còn chấm công cần biết "tháng đã chốt chưa". Nếu attendance gọi `PayrollService` thì thành vòng. Ở đây `AttendanceMonthLock` hỏi thẳng bảng `payroll` bằng SQL: hai module phụ thuộc chung vào *bảng*, còn *code* chỉ phụ thuộc một chiều.
- **Làm tròn từng phần, rồi mới cộng** (BR-PAY-01): 41,235 tấn × 52.500 = 2.164.837,5, làm tròn thành 2.164.838; cộng thêm tiền công là ra tổng. Nếu cộng trước rồi mới làm tròn thì tổng có thể lệch 1đ so với các dòng in trên phiếu lương. Khi đó người nhận cộng tay sẽ không khớp với con số cuối.

### Lỗi đã gặp

- **Test đọc dữ liệu cũ trong bộ nhớ đệm Hibernate**: test dùng `UPDATE employee SET active = false` bằng JdbcTemplate, rồi gọi API bảng công, nhưng kết quả vẫn ra `active: true`. Nguyên nhân: test chạy trong một transaction duy nhất, và Hibernate đã nạp nhân viên đó vào persistence context từ lần gọi trước. Câu UPDATE viết tay đi thẳng xuống DB nên Hibernate không biết. Ở môi trường thật, mỗi request có persistence context riêng nên không gặp lỗi này. Cách sửa: thao tác qua API (cho nghỉ việc bằng `DELETE /api/employees/{id}`), đúng như người dùng làm.

- **Form báo "chưa nhập đơn giá" dù đã gõ đủ (bạn phát hiện khi chạy thử)**: `Form.Item` gắn `value`/`onChange` vào component con *trực tiếp* của nó. Ô tiền được bọc trong `PriceInput` tự viết, nhưng `PriceInput` không chuyển tiếp hai prop này xuống `InputNumber`. Vì vậy ô vẫn hiện số người dùng gõ, còn form thì luôn thấy `undefined`. Cách sửa: `PriceInput(props)` rồi `<InputNumber {...props} />`. Quy tắc chung: component tự viết đặt trong `Form.Item` phải nhận và chuyển tiếp `value`/`onChange`. Test hiện có không bắt được lỗi này vì chỉ test hàm API, không test component. Muốn bắt được thì cần thư viện test giao diện (React Testing Library), mà thư viện này chưa có trong dự án.
- **Thanh cuộn ngang trong modal**: `Row gutter` tạo khoảng cách bằng margin âm hai bên. Thân modal lại đặt `overflow: auto`, nên phần margin thừa ra sinh thanh cuộn ngang. Trong modal nên dùng `Flex gap` thay cho `Row gutter`.

### Câu hỏi ôn tập (để cuối dự án)

1. Vì sao bảng lương đã chốt phải lưu đơn giá, chứ không JOIN sang `employee`?
2. `SELECT … FOR UPDATE` và `pg_advisory_xact_lock` khác nhau thế nào? Vì sao chốt lương dùng cái sau?
3. Nếu `AttendanceService.record` kiểm tra "đã chốt chưa" *trước* khi lấy khóa tháng thì kịch bản lỗi diễn ra thế nào?
4. Viết ràng buộc "mỗi tháng chỉ một bảng lương FINALIZED" bằng SQL.
